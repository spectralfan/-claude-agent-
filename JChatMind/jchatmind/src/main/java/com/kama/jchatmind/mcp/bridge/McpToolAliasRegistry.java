package com.kama.jchatmind.mcp.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP / 终端工具别名统一配置（单一来源，避免为每个工具名单独写注册逻辑）。
 */
public final class McpToolAliasRegistry {

    public static final Set<String> TERMINAL_TOOL_NAMES = Set.of(
            "run_terminal_cmd", "bash", "shell",
            "shell_exec", "shell_execute", "execute_command"
    );

    /** 规范 MCP 工具名 → LLM 可能使用的别名 */
    private static final Map<String, List<String>> CANONICAL_TO_ALIASES = Map.of(
            "execute_command", List.of("run_terminal_cmd", "bash", "shell"),
            "shell_exec", List.of("run_terminal_cmd", "bash", "shell"),
            "shell_execute", List.of("run_terminal_cmd", "bash", "shell")
    );

    private McpToolAliasRegistry() {
    }

    public static boolean isTerminalToolName(String name) {
        return name != null && TERMINAL_TOOL_NAMES.contains(name);
    }

    /**
     * 将规范名解析为回调注册名：精确匹配 → 去连接前缀 → 后缀匹配。
     */
    public static String resolveCanonicalName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        if (CANONICAL_TO_ALIASES.containsKey(toolName)) {
            return toolName;
        }
        for (String canonical : CANONICAL_TO_ALIASES.keySet()) {
            if (toolName.endsWith("_" + canonical) || toolName.endsWith(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    public static List<String> aliasesFor(String canonicalOrActualName) {
        String canonical = CANONICAL_TO_ALIASES.containsKey(canonicalOrActualName)
                ? canonicalOrActualName
                : resolveCanonicalName(canonicalOrActualName);
        if (canonical == null) {
            return List.of();
        }
        return CANONICAL_TO_ALIASES.getOrDefault(canonical, List.of());
    }

    /**
     * 为 ChatClient / ToolCallingManager 展开别名：每个别名指向同一 delegate，无需手写 N 份注册。
     */
    public static List<org.springframework.ai.tool.ToolCallback> expandAliases(
            List<org.springframework.ai.tool.ToolCallback> callbacks) {
        Map<String, org.springframework.ai.tool.ToolCallback> byName = new LinkedHashMap<>();
        for (org.springframework.ai.tool.ToolCallback cb : callbacks) {
            byName.putIfAbsent(cb.getToolDefinition().name(), cb);
        }
        List<org.springframework.ai.tool.ToolCallback> expanded = new ArrayList<>(callbacks);
        for (org.springframework.ai.tool.ToolCallback cb : callbacks) {
            String name = cb.getToolDefinition().name();
            String canonical = resolveCanonicalName(name);
            if (canonical == null && !CANONICAL_TO_ALIASES.containsKey(name)) {
                continue;
            }
            org.springframework.ai.tool.ToolCallback delegate = cb;
            for (String alias : aliasesFor(name)) {
                if (!byName.containsKey(alias)) {
                    AliasedToolCallback aliased = new AliasedToolCallback(alias, delegate);
                    expanded.add(aliased);
                    byName.put(alias, aliased);
                }
            }
        }
        return expanded;
    }
}
