package com.kama.jchatmind.mcp.bootstrap;

import com.kama.jchatmind.mcp.bridge.McpShellCommandPolicy;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.config.McpShellPlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP shell 启动自检与健康快照：在后端启动时跑 command-runner 单测，避免联调时才发现 runner 版本过旧。
 */
@Slf4j
@Component
public class McpShellHealthService {

    private static final Pattern RUNNER_VERSION = Pattern.compile("RUNNER_VERSION\\s*=\\s*\"([^\"]+)\"");

    private volatile String runnerVersion;
    private volatile Boolean selfTestPassed;
    private volatile String selfTestDetail;

    public void ensureSelfTest(McpProperties mcpProperties) {
        if (selfTestPassed != null) {
            return;
        }
        runSelfTest(resolveScriptsDir(mcpProperties.getProxy().getScriptsDir()));
    }

    public void runSelfTest(Path scriptsDir) {
        if (scriptsDir == null || !Files.isDirectory(scriptsDir)) {
            record(false, "scripts 目录不存在", null);
            return;
        }
        Path runnerScript = scriptsDir.resolve("command-runner.mjs");
        Path smokeScript = scriptsDir.resolve("command-runner.smoke.mjs");
        if (!Files.isRegularFile(runnerScript) || !Files.isRegularFile(smokeScript)) {
            record(false, "缺少 command-runner.mjs 或 command-runner.smoke.mjs", readRunnerVersion(runnerScript));
            return;
        }
        String version = readRunnerVersion(runnerScript);
        try {
            List<String> command = nodeCommand(smokeScript.getFileName().toString());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(scriptsDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = readProcessOutput(process);
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                record(false, "command-runner 单测超时", version);
                return;
            }
            int exit = process.exitValue();
            if (exit == 0) {
                record(true, "command-runner smoke 自检通过", version);
                log.info("MCP shell 自检通过，runner={}", version);
            } else {
                String detail = truncate(output, 500);
                record(false, "command-runner smoke 自检失败 exit=" + exit + ": " + detail, version);
                log.warn("MCP shell 自检失败: {}", detail);
            }
        } catch (Exception e) {
            record(false, "自检异常: " + e.getMessage(), version);
            log.warn("MCP shell 自检异常: {}", e.getMessage());
        }
    }

    public Map<String, Object> healthSnapshot(McpProperties mcpProperties) {
        Map<String, Object> body = new LinkedHashMap<>();
        McpProperties.Shell shell = mcpProperties.getShell();
        body.put("shellPlatform", McpShellPlatform.envPlatform(shell));
        body.put("shellExecutor", McpShellPlatform.resolvedExecutor(shell));
        body.put("hostOs", McpShellPlatform.isHostWindows() ? "windows" : "posix");
        body.put("policyEnabled", shell.isPolicyEnabled());
        body.put("policyVersion", McpShellCommandPolicy.POLICY_VERSION);
        body.put("runnerVersion", runnerVersion != null ? runnerVersion : "unknown");
        body.put("selfTestPassed", selfTestPassed);
        body.put("selfTestDetail", selfTestDetail != null ? selfTestDetail : "尚未执行自检");
        return body;
    }

    private void record(boolean passed, String detail, String version) {
        this.selfTestPassed = passed;
        this.selfTestDetail = detail;
        if (version != null && !version.isBlank()) {
            this.runnerVersion = version;
        }
    }

    private static String readRunnerVersion(Path runnerScript) {
        if (runnerScript == null || !Files.isRegularFile(runnerScript)) {
            return null;
        }
        try {
            String content = Files.readString(runnerScript);
            Matcher m = RUNNER_VERSION.matcher(content);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IOException ignore) {
        }
        return null;
    }

    private static List<String> nodeCommand(String scriptName) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(isWindows() ? "node.exe" : "node");
        command.add("--test");
        command.add(scriptName);
        return command;
    }

    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static Path resolveScriptsDir(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path fromUserDir = Path.of(System.getProperty("user.dir")).resolve(path).normalize();
        if (Files.isDirectory(fromUserDir)) {
            return fromUserDir;
        }
        Path moduleFallback = Path.of(System.getProperty("user.dir"))
                .resolve("JChatMind/jchatmind/scripts/mcp")
                .normalize();
        if (Files.isDirectory(moduleFallback)) {
            return moduleFallback;
        }
        return fromUserDir;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
