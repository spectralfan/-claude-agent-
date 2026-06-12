package com.kama.jchatmind.mcp.integration;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.mcp.bridge.McpCallRecorder;
import com.kama.jchatmind.mcp.bridge.McpShellArgumentEnricher;
import com.kama.jchatmind.mcp.bridge.McpShellCommandPolicy;
import com.kama.jchatmind.mcp.bridge.RecordingToolCallback;
import com.kama.jchatmind.mcp.config.McpProperties;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final List<McpSyncClient> mcpSyncClients;
    private final McpProperties properties;
    private final McpCallRecorder recorder;
    private final CodingMcpOutputBridge codingMcpOutputBridge;
    private final McpShellArgumentEnricher shellArgumentEnricher;
    private final McpShellCommandPolicy shellCommandPolicy;
    private final List<ToolCallback> cachedCallbacks = new CopyOnWriteArrayList<>();

    public McpClientManager(List<McpSyncClient> clients, McpProperties props,
                            McpCallRecorder rec, CodingMcpOutputBridge bridge,
                            McpShellArgumentEnricher enricher, McpShellCommandPolicy policy) {
        this.mcpSyncClients = clients != null ? clients : List.of();
        this.properties = props;
        this.recorder = rec;
        this.codingMcpOutputBridge = bridge;
        this.shellArgumentEnricher = enricher;
        this.shellCommandPolicy = policy;
    }

    @PostConstruct
    public void init() {
        refresh();
        log.info("McpClientManager initialized with {} MCP clients, {} tools",
                mcpSyncClients.size(), cachedCallbacks.size());
    }

    public synchronized void refresh() {
        cachedCallbacks.clear();
        for (McpSyncClient client : mcpSyncClients) {
            String serverId = resolveServerId(client);
            try {
                ToolCallback[] callbacks = new SyncMcpToolCallbackProvider(client).getToolCallbacks();
                log.info("MCP client[{}] discovered {} tools: {}", serverId, callbacks.length,
                        Arrays.stream(callbacks).map(c -> c.getToolDefinition().name()).toList());
                for (ToolCallback cb : callbacks) {
                    cachedCallbacks.add(new RecordingToolCallback(cb, serverId, recorder,
                            properties.isRecordCalls(), codingMcpOutputBridge,
                            shellArgumentEnricher, shellCommandPolicy));
                }
            } catch (Exception e) {
                log.warn("MCP client[{}] discovery failed: {}", serverId, e.getMessage());
            }
        }
    }

    public List<ToolCallback> getAllToolCallbacks() {
        return List.copyOf(cachedCallbacks);
    }

    private String resolveServerId(McpSyncClient client) {
        try {
            if (client.getServerInfo() != null && client.getServerInfo().name() != null) {
                return client.getServerInfo().name();
            }
        } catch (Exception ignored) {}
        return "mcp";
    }
}