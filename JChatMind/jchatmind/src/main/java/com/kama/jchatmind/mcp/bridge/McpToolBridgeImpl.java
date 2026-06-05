package com.kama.jchatmind.mcp.bridge;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.mcp.config.McpProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于 Spring AI 官方 MCP 客户端实现桥接。
 * 注入 {@link McpSyncClient}（由 spring.ai.mcp.client 自动装配，可能为 0 个），
 * 逐 client 取工具回调并包成 {@link RecordingToolCallback}。
 */
@Slf4j
@Component
public class McpToolBridgeImpl implements McpToolBridge {

    private final List<McpSyncClient> mcpSyncClients;
    private final McpProperties properties;
    private final McpCallRecorder recorder;
    private final CodingMcpOutputBridge codingMcpOutputBridge;

    @Autowired
    public McpToolBridgeImpl(List<McpSyncClient> mcpSyncClients,
                             McpProperties properties,
                             McpCallRecorder recorder,
                             CodingMcpOutputBridge codingMcpOutputBridge) {
        this.mcpSyncClients = mcpSyncClients != null ? mcpSyncClients : List.of();
        this.properties = properties;
        this.recorder = recorder;
        this.codingMcpOutputBridge = codingMcpOutputBridge;
    }

    @Override
    public List<ToolCallback> getAllToolCallbacks() {
        if (mcpSyncClients.isEmpty()) {
            log.warn("MCP 工具桥接：当前无已连接的 McpSyncClient Bean");
            return List.of();
        }
        List<ToolCallback> result = new ArrayList<>();
        for (McpSyncClient client : mcpSyncClients) {
            String serverId = resolveServerId(client);
            try {
                ToolCallback[] callbacks = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
                log.info("MCP client[{}] 发现 {} 个工具: {}", serverId, callbacks.length,
                        Arrays.stream(callbacks)
                                .map(cb -> cb.getToolDefinition().name())
                                .toList());
                for (ToolCallback cb : callbacks) {
                    result.add(new RecordingToolCallback(
                            cb, serverId, recorder, properties.isRecordCalls(), codingMcpOutputBridge));
                }
            } catch (Exception e) {
                log.warn("从 MCP client[{}] 获取工具失败，已跳过: {}", serverId, e.getMessage(), e);
            }
        }
        return result;
    }

    private String resolveServerId(McpSyncClient client) {
        try {
            if (client.getServerInfo() != null && client.getServerInfo().name() != null) {
                return client.getServerInfo().name();
            }
        } catch (Exception ignore) {
            // 部分实现未初始化时取不到 serverInfo
        }
        return "mcp";
    }
}
