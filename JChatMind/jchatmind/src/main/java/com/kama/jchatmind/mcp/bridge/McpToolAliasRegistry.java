package com.kama.jchatmind.mcp.bridge;

import java.util.*;

public final class McpToolAliasRegistry {

    public static final String CANONICAL_SHELL = "bash";

    public static final Set<String> TERMINAL_TOOL_NAMES = Set.of(
            "bash", "run_terminal_cmd", "shell",
            "shell_exec", "shell_execute", "execute_command"
    );

    private static final Map<String, List<String>> ALIASES = Map.of(
            "bash", List.of("run_terminal_cmd", "shell", "shell_exec", "shell_execute", "execute_command")
    );

    private McpToolAliasRegistry() {}

    public static boolean isTerminalToolName(String name) {
        return name != null && TERMINAL_TOOL_NAMES.contains(name);
    }

    public static String resolveCanonicalName(String toolName) {
        if (toolName == null) return null;
        if (ALIASES.containsKey(toolName)) return toolName;
        for (Map.Entry<String, List<String>> e : ALIASES.entrySet()) {
            if (e.getValue().contains(toolName)) return e.getKey();
        }
        return null;
    }

    public static List<String> aliasesFor(String name) {
        for (Map.Entry<String, List<String>> e : ALIASES.entrySet()) {
            if (e.getKey().equals(name)) return e.getValue();
        }
        return List.of();
    }

    public static List<org.springframework.ai.tool.ToolCallback> expandAliases(
            List<org.springframework.ai.tool.ToolCallback> callbacks) {
        Map<String, org.springframework.ai.tool.ToolCallback> byName = new LinkedHashMap<>();
        for (var cb : callbacks) byName.putIfAbsent(cb.getToolDefinition().name(), cb);
        List<org.springframework.ai.tool.ToolCallback> expanded = new ArrayList<>(callbacks);
        for (var cb : callbacks) {
            String name = cb.getToolDefinition().name();
            String canonical = resolveCanonicalName(name);
            if (canonical == null) continue;
            var delegate = cb;
            for (String alias : aliasesFor(canonical)) {
                if (!byName.containsKey(alias)) {
                    expanded.add(new AliasedToolCallback(alias, delegate));
                    byName.put(alias, delegate);
                }
            }
        }
        return expanded;
    }
}