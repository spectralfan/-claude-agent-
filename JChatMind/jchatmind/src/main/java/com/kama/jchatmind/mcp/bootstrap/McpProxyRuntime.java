package com.kama.jchatmind.mcp.bootstrap;

import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.config.McpShellPlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MCP proxy 子进程生命周期（可在 ApplicationContextInitializer 阶段启动，早于 Spring AI SSE 客户端）。
 */
@Slf4j
final class McpProxyRuntime {

    private static final Object LOCK = new Object();
    private static volatile Process proxyProcess;
    private static volatile Thread logDrainThread;
    private static volatile boolean started;
    /** 仅当本进程由 JChatMind 拉起时为 true；外部 start-mcp-proxy.ps1 占端口时不杀 */
    private static volatile boolean startedByUs;

    private McpProxyRuntime() {
    }

    static boolean ensureStarted(McpProxyStartConfig config) {
        synchronized (LOCK) {
            if (started && (proxyProcess == null || proxyProcess.isAlive() || isPortOpen("127.0.0.1", config.port()))) {
                return isPortOpen("127.0.0.1", config.port());
            }
            if (isPortOpen("127.0.0.1", config.port())) {
                log.info("MCP proxy 端口 {} 已在监听，跳过启动（外部 proxy，关后端时不强杀端口）", config.port());
                started = true;
                startedByUs = false;
                return true;
            }

            Path scriptsDir = resolveScriptsDir(config.scriptsDir());
            Path serverScript = scriptsDir.resolve(config.serverScript());
            if (!Files.isRegularFile(serverScript)) {
                log.error("MCP proxy 启动失败：未找到 server 脚本 {}", serverScript.toAbsolutePath());
                return false;
            }

            try {
                if (config.npmInstallIfMissing()) {
                    ensureNodeModules(scriptsDir);
                }
                log.info("正在启动 MCP proxy: npx mcp-proxy --port {} (cwd={})", config.port(), scriptsDir);
                proxyProcess = startProxyProcess(scriptsDir, serverScript, config);
                drainProcessLogs(proxyProcess);
                if (!waitForPort("127.0.0.1", config.port(), config.startupWaitMs())) {
                    log.error("MCP proxy 在 {}ms 内未监听端口 {}", config.startupWaitMs(), config.port());
                    return false;
                }
                log.info("MCP proxy 已启动，监听 http://127.0.0.1:{} (pid={})", config.port(), proxyProcess.pid());
                started = true;
                startedByUs = true;
                return true;
            } catch (Exception e) {
                log.error("MCP proxy 启动失败: {}", e.getMessage(), e);
                return false;
            }
        }
    }

    static void shutdown(McpProxyStartConfig config, boolean stopOnShutdown, long sseDrainMs) {
        if (!stopOnShutdown) {
            return;
        }
        synchronized (LOCK) {
            started = false;
            if (sseDrainMs > 0) {
                log.debug("等待 MCP SSE 客户端断开 ({}ms)…", sseDrainMs);
                try {
                    Thread.sleep(sseDrainMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (proxyProcess != null && proxyProcess.isAlive()) {
                log.info("正在停止 MCP proxy 子进程 (pid={})", proxyProcess.pid());
                destroyProcess(proxyProcess);
                proxyProcess = null;
                startedByUs = false;
                return;
            }
            if (startedByUs && isPortOpen("127.0.0.1", config.port())) {
                log.info("正在停止本进程拉起的 MCP proxy（端口 {}）", config.port());
                stopListenerOnPort(config.port());
            }
            startedByUs = false;
        }
    }

    static boolean isManagedProcessAlive() {
        return startedByUs && proxyProcess != null && proxyProcess.isAlive();
    }

    static McpProxyStartConfig configFrom(Environment env) {
        McpProperties.Shell shell = new McpProperties.Shell();
        shell.setPlatform(env.getProperty("mcp.shell.platform", "auto"));
        shell.setExecutor(env.getProperty("mcp.shell.executor", "auto"));

        int configuredPort = env.getProperty("mcp.proxy.port", Integer.class, 3000);
        String proxyUrl = env.getProperty("spring.ai.mcp.client.sse.connections.proxy.url", "http://localhost:3000");

        return new McpProxyStartConfig(
                resolvePort(configuredPort, proxyUrl),
                env.getProperty("mcp.proxy.scripts-dir", "scripts/mcp"),
                env.getProperty("mcp.proxy.server-script", "jchatmind-shell-mcp.mjs"),
                env.getProperty("mcp.proxy.startup-wait-ms", Long.class, 15_000L),
                env.getProperty("mcp.proxy.npm-install-if-missing", Boolean.class, true),
                McpShellPlatform.envPlatform(shell),
                McpShellPlatform.resolvedExecutor(shell),
                env.getProperty("coding.workspace.preview-port", Integer.class, 5500)
        );
    }

    static McpProxyStartConfig configFrom(McpProperties mcpProperties,
                                          CodingPreviewPort previewPort,
                                          String proxyUrl) {
        McpProperties.Proxy proxy = mcpProperties.getProxy();
        McpProperties.Shell shell = mcpProperties.getShell();
        return new McpProxyStartConfig(
                resolvePort(proxy.getPort(), proxyUrl),
                proxy.getScriptsDir(),
                proxy.getServerScript(),
                proxy.getStartupWaitMs(),
                proxy.isNpmInstallIfMissing(),
                McpShellPlatform.envPlatform(shell),
                McpShellPlatform.resolvedExecutor(shell),
                previewPort.value()
        );
    }

    private static int resolvePort(int configuredPort, String proxyUrl) {
        if (!StringUtils.hasText(proxyUrl)) {
            return configuredPort;
        }
        try {
            var uri = java.net.URI.create(proxyUrl.trim());
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        } catch (Exception e) {
            return configuredPort;
        }
    }

    private static Path resolveScriptsDir(String configured) {
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path fromUserDir = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
        if (Files.isDirectory(fromUserDir)) {
            return fromUserDir;
        }
        Path moduleFallback = Paths.get(System.getProperty("user.dir"))
                .resolve("scripts/mcp")
                .normalize();
        if (Files.isDirectory(moduleFallback)) {
            log.warn("mcp.proxy.scripts-dir 相对路径未找到，回退到 {}", moduleFallback);
            return moduleFallback;
        }
        return fromUserDir;
    }

    private static void ensureNodeModules(Path scriptsDir) throws IOException, InterruptedException {
        if (Files.isDirectory(scriptsDir.resolve("node_modules"))) {
            return;
        }
        log.info("MCP scripts 目录缺少 node_modules，执行 npm install: {}", scriptsDir);
        ProcessBuilder pb = new ProcessBuilder(npmCommand("install", "--silent"));
        pb.directory(scriptsDir.toFile());
        pb.redirectErrorStream(true);
        Process install = pb.start();
        if (!install.waitFor(120, TimeUnit.SECONDS)) {
            install.destroyForcibly();
            throw new IOException("npm install 超时");
        }
        if (install.exitValue() != 0) {
            throw new IOException("npm install 失败，exit=" + install.exitValue());
        }
    }

    private static Process startProxyProcess(Path scriptsDir, Path serverScript, McpProxyStartConfig config)
            throws IOException {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(isWindows() ? "npx.cmd" : "npx");
        command.add("-y");
        command.add("mcp-proxy");
        command.add("--port");
        command.add(String.valueOf(config.port()));
        command.add("--server");
        command.add("sse");
        command.add("--");
        command.add("node");
        command.add(serverScript.getFileName().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scriptsDir.toFile());
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        prependPath(env, "Z:\\Node.js");
        prependPath(env, System.getenv("ProgramFiles") + "\\nodejs");
        prependPath(env, System.getenv("LOCALAPPDATA") + "\\Programs\\node");
        env.put("JCHATMIND_MCP_PLATFORM", config.shellPlatform());
        env.put("JCHATMIND_MCP_EXECUTOR", config.shellExecutor());
        env.put("JCHATMIND_PREVIEW_PORT", String.valueOf(config.previewPort()));
        env.put("JCHATMIND_RESERVED_PORTS", "8080,3000,5173");
        log.info("MCP shell 子进程环境: platform={}, executor={}, previewPort={}",
                config.shellPlatform(), config.shellExecutor(), config.previewPort());
        return pb.start();
    }

    private static void drainProcessLogs(Process process) {
        logDrainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[mcp-proxy] {}", line);
                }
            } catch (IOException e) {
                log.debug("MCP proxy 日志流结束: {}", e.getMessage());
            }
        }, "mcp-proxy-log");
        logDrainThread.setDaemon(true);
        logDrainThread.start();
    }

    private static boolean waitForPort(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(host, port)) {
                return true;
            }
            Thread.sleep(250);
        }
        return isPortOpen(host, port);
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void stopListenerOnPort(int port) {
        if (isWindows()) {
            runQuietly(List.of(
                    "powershell.exe", "-NoProfile", "-Command",
                    "Get-NetTCPConnection -LocalPort " + port + " -State Listen -ErrorAction SilentlyContinue | "
                            + "Select-Object -ExpandProperty OwningProcess -Unique | "
                            + "ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }"
            ));
            return;
        }
        runQuietly(List.of("sh", "-c",
                "lsof -ti :" + port + " | xargs -r kill -TERM 2>/dev/null || fuser -k " + port + "/tcp 2>/dev/null || true"));
    }

    private static void destroyProcess(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (isWindows()) {
                    runQuietly(List.of("cmd.exe", "/c", "taskkill", "/F", "/T", "/PID", String.valueOf(process.pid())));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void runQuietly(List<String> command) {
        try {
            new ProcessBuilder(command).start().waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignore) {
        }
    }

    private static List<String> npmCommand(String... args) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("npm.cmd");
        } else {
            command.add("npm");
        }
        command.addAll(List.of(args));
        return command;
    }

    private static void prependPath(Map<String, String> env, String dir) {
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path p = Paths.get(dir);
        if (!Files.isDirectory(p)) {
            return;
        }
        String pathKey = isWindows() ? "Path" : "PATH";
        String existing = env.getOrDefault(pathKey, "");
        String segment = p.toAbsolutePath().toString();
        if (!existing.toLowerCase().contains(segment.toLowerCase())) {
            env.put(pathKey, segment + (existing.isBlank() ? "" : (isWindows() ? ";" : ":")) + existing);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    record McpProxyStartConfig(
            int port,
            String scriptsDir,
            String serverScript,
            long startupWaitMs,
            boolean npmInstallIfMissing,
            String shellPlatform,
            String shellExecutor,
            int previewPort
    ) {
    }

    @FunctionalInterface
    interface CodingPreviewPort {
        int value();
    }
}
