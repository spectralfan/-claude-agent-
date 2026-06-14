package com.kama.jchatmind.mcp.integration;

import com.kama.jchatmind.mcp.bridge.McpToolBridge;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.mapper.McpToolCallMapper;
import com.kama.jchatmind.mcp.model.dto.McpCallResultDTO;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpIntegrationImpl implements McpIntegration {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final McpToolBridge bridge;
    private final McpProperties properties;
    private final McpToolCallMapper callMapper;

    public static boolean isTerminalToolName(String name) {
        return "execute_command".equals(name);
    }

    @Override
    public List<ToolCallback> getToolsForAgent(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new HashSet<>(allowedToolNames);
        boolean wantsTerminal = allowed.contains("execute_command");

        List<ToolCallback> result = new ArrayList<>();
        Set<String> registeredNames = new HashSet<>();
        for (ToolCallback cb : bridge.getAllToolCallbacks()) {
            String name = cb.getToolDefinition().name();
            if (matches(name, allowed) || (wantsTerminal && isShellMcpTool(name))) {
                if (registeredNames.add(name)) {
                    result.add(cb);
                }
            }
        }
        if (wantsTerminal && result.stream().noneMatch(cb -> isShellMcpTool(cb.getToolDefinition().name()))) {
            log.warn("Agent 白名单含终端工具，但当前未发现 MCP shell 工具（请检查 mcp-proxy :3000 与 SSE 连接）");
        }
        return result;
    }

    @Override
    public List<ToolCallback> getShellToolCallbacks() {
        List<ToolCallback> result = new ArrayList<>();
        for (ToolCallback cb : bridge.getAllToolCallbacks()) {
            if (isShellMcpTool(cb.getToolDefinition().name())) {
                result.add(cb);
            }
        }
        return result;
    }

    private boolean isShellMcpTool(String name) {
        return "execute_command".equals(name);
    }

    /**
     * 名称匹配：先精确匹配；开启去前缀兜底时，再用「最后一个下划线后的部分」匹配
     * （应对 SDK 在工具名冲突时加的连接名前缀，如 alt_1_toolName）。
     */
    private boolean matches(String name, Set<String> allowed) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (allowed.contains(name)) {
            return true;
        }
        if (!properties.isToolNamePrefixStrip()) {
            return false;
        }
        int idx = name.lastIndexOf('_');
        if (idx > 0 && idx < name.length() - 1 && allowed.contains(name.substring(idx + 1))) {
            return true;
        }
        for (String allowedName : allowed) {
            if (name.endsWith("_" + allowedName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<McpCallResultDTO> getCallHistory(String serverId, String toolName,
                                                 LocalDateTime since, int limit) {
        int effLimit = limit > 0 ? limit : DEFAULT_HISTORY_LIMIT;
        List<McpToolCall> rows = callMapper.selectHistory(serverId, toolName, since, effLimit);
        List<McpCallResultDTO> result = new ArrayList<>(rows.size());
        for (McpToolCall c : rows) {
            result.add(McpCallResultDTO.builder()
                    .serverId(c.getServerId())
                    .toolName(c.getToolName())
                    .status(c.getStatus())
                    .result(c.getResult())
                    .errorMessage(c.getErrorMessage())
                    .durationMs(c.getDurationMs())
                    .createdAt(c.getCreatedAt())
                    .build());
        }
        return result;
    }

    @Override
    public Map<String, Long> getToolUsageStats(String serverId) {
        List<Map<String, Object>> rows = callMapper.usageStats(serverId);
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("tool_name");
            Object cnt = row.get("cnt");
            if (name != null) {
                stats.put(name.toString(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        return stats;
    }
}
