package com.kama.jchatmind.mcp.bridge;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * MCP 工具桥接：把已连接的 MCP server 暴露的工具转成 Spring AI ToolCallback（已包埋点）。
 */
public interface McpToolBridge {

    /** 返回所有已连接 MCP server 的工具回调（包装了埋点）。无连接时返回空列表。 */
    List<ToolCallback> getAllToolCallbacks();
}
