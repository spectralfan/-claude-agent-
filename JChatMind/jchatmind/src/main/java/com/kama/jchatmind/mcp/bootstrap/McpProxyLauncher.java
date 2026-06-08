package com.kama.jchatmind.mcp.bootstrap;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * MCP proxy 生命周期：启动由 {@link McpProxyApplicationContextInitializer} 提前完成；
 * 此处负责自检与优雅关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.proxy", name = "auto-start", havingValue = "true")
@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "true")
public class McpProxyLauncher implements SmartLifecycle {

    private final McpProperties mcpProperties;
    private final CodingProperties codingProperties;
    private final McpShellHealthService shellHealthService;

    @Value("${spring.ai.mcp.client.sse.connections.proxy.url:http://localhost:3000}")
    private String proxyUrl;

    private volatile boolean running;

    @Override
    public void start() {
        McpProxyRuntime.McpProxyStartConfig config = buildConfig();
        log.info("MCP 生命周期检查：port={}, scriptsDir={}", config.port(), config.scriptsDir());

        boolean ready = McpProxyRuntime.ensureStarted(config);
        if (ready) {
            Path scriptsDir = resolveScriptsDir(config.scriptsDir());
            shellHealthService.runSelfTest(scriptsDir);
            running = true;
        } else {
            log.warn("MCP proxy 未就绪，Spring AI SSE 可能报 ConnectException；请检查 Node/npx 与端口 {}", config.port());
        }
    }

    @Override
    public void stop() {
        running = false;
        McpProxyRuntime.McpProxyStartConfig config = buildConfig();
        long drainMs = mcpProperties.getProxy().getSseDrainMs();
        McpProxyRuntime.shutdown(config, mcpProperties.getProxy().isStopOnShutdown(), drainMs);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 停止时尽量最后执行（MIN_VALUE 在 SmartLifecycle.stop 中最后关闭）
        return Integer.MIN_VALUE;
    }

    private McpProxyRuntime.McpProxyStartConfig buildConfig() {
        return McpProxyRuntime.configFrom(
                mcpProperties,
                codingProperties.getWorkspace()::getPreviewPort,
                proxyUrl
        );
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
        return Paths.get(System.getProperty("user.dir"))
                .resolve("JChatMind/jchatmind/scripts/mcp")
                .normalize();
    }
}
