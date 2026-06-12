package com.kama.jchatmind.mcp.bridge;

import com.kama.jchatmind.mcp.integration.McpClientManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class McpToolBridgeImpl implements McpToolBridge {

    private final McpClientManager clientManager;

    public McpToolBridgeImpl(McpClientManager clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public List<ToolCallback> getAllToolCallbacks() {
        List<ToolCallback> tools = clientManager.getAllToolCallbacks();
        if (tools.isEmpty()) {
            log.warn("MCP 工具桥接：当前无已连接的 MCP 客户端");
        }
        return tools;
    }
}