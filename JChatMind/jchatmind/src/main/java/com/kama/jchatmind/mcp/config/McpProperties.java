package com.kama.jchatmind.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 集成模块配置，前缀 mcp。
 * 注意与 spring.ai.mcp.client 区分：后者负责与 MCP server/proxy 的连接与发现，
 * 这里只负责「是否把 MCP 工具注入 Agent」与「调用埋点」。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /** 是否把发现到的 MCP 工具按 allowedTools 白名单注入 Agent；关闭时零行为变化 */
    private boolean enabled = false;

    /** 是否异步记录工具调用到 t_mcp_tool_call */
    private boolean recordCalls = true;

    /** 匹配 allowedTools 时，是否对 SDK 可能添加的连接名前缀做去前缀兜底匹配 */
    private boolean toolNamePrefixStrip = true;

    /**
     * Shell 执行平台（与 spring.ai.mcp.client 连接方式无关，作用于 jchatmind-shell-mcp 子进程）。
     * Windows 开发机务必保持 platform=auto 或 windows，避免 Agent 按 Linux 命令验证。
     */
    private Shell shell = new Shell();

    /** 内置 mcp-proxy 子进程自动启动配置 */
    private Proxy proxy = new Proxy();

    @Data
    public static class Shell {

        /**
         * auto=按 OS 检测；windows=PowerShell+Windows 命令翻译；posix=sh+Unix 翻译。
         */
        private String platform = "auto";

        /**
         * auto=windows→powershell、posix→sh；可显式 powershell|cmd|sh。
         * 不推荐 cmd：引号与路径易失败，仅兼容旧行为。
         */
        private String executor = "auto";

        /**
         * 是否在 Java 侧拦截高危 MCP shell 命令（node -e、http-server、8080 等），
         * 引导 Agent 使用 coding_verify_tools。
         */
        private boolean policyEnabled = true;
    }

    @Data
    public static class Proxy {

        /**
         * 后端启动时自动拉起 scripts/mcp 下的 mcp-proxy（需 spring.ai.mcp.client.enabled=true）。
         * 若端口已被占用则跳过，便于与手动 start-mcp-proxy.ps1 共存。
         */
        private boolean autoStart = false;

        private int port = 3000;

        /** 相对 user.dir 或绝对路径 */
        private String scriptsDir = "scripts/mcp";

        private String serverScript = "jchatmind-shell-mcp.mjs";

        /** 等待代理监听端口的超时（毫秒） */
        private long startupWaitMs = 12_000;

        /** 后端退出时是否结束子进程 */
        private boolean stopOnShutdown = true;

        /** 关后端时先等待 SSE 客户端断开，再杀 proxy（毫秒） */
        private long shutdownDelayMs = 1200;

        /** node_modules 缺失时是否执行 npm install */
        private boolean npmInstallIfMissing = true;

        /** 关后端时先等待 SSE 客户端断开，再杀 proxy（毫秒） */
        private long sseDrainMs = 1200;
    }
}
