package com.kama.jchatmind.mcp.integration;

import com.kama.jchatmind.mcp.bridge.AliasedToolCallback;
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

    private static final Map<String, List<String>> MCP_TERMINAL_ALIASES = Map.of(
            "shell_exec", List.of("run_terminal_cmd", "bash", "shell"),
            "shell_execute", List.of("run_terminal_cmd", "bash", "shell"),
            "execute_command", List.of("run_terminal_cmd", "bash", "shell")
    );

    private static final Set<String> TERMINAL_ALLOW_NAMES = Set.of(
            "run_terminal_cmd", "bash", "shell", "shell_exec", "shell_execute", "execute_command"
    );

    public static boolean isTerminalToolName(String name) {
        return name != null && TERMINAL_ALLOW_NAMES.contains(name);
    }

    @Override
    public List<ToolCallback> getToolsForAgent(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new HashSet<>(allowedToolNames);
        boolean wantsTerminal = allowed.stream().anyMatch(TERMINAL_ALLOW_NAMES::contains);

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
        for (ToolCallback cb : new ArrayList<>(result)) {
            List<String> aliases = resolveTerminalAliases(cb.getToolDefinition().name());
            if (aliases == null) {
                continue;
            }
            for (String alias : aliases) {
                if (!allowed.contains(alias) || registeredNames.contains(alias)) {
                    continue;
                }
                result.add(new AliasedToolCallback(alias, cb));
                registeredNames.add(alias);
            }
        }
        return result;
    }

    private List<String> resolveTerminalAliases(String canonical) {
        if (MCP_TERMINAL_ALIASES.containsKey(canonical)) {
            return MCP_TERMINAL_ALIASES.get(canonical);
        }
        for (Map.Entry<String, List<String>> entry : MCP_TERMINAL_ALIASES.entrySet()) {
            String key = entry.getKey();
            if (canonical.endsWith("_" + key) || canonical.endsWith(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isShellMcpTool(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase();
        return lower.contains("shell_exec") || lower.endsWith("_shell_execute")
                || "shell_execute".equals(lower)
                || lower.contains("execute_command") || "execute_command".equals(lower);
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
