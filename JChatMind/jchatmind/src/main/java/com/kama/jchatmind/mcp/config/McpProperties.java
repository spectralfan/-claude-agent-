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
}
