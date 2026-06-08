package com.kama.jchatmind.mcp.bridge;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一按「精确名 → 别名 → MCP 连接前缀 → 实时桥接」解析工具。
 */
public class AliasAwareToolCallbackResolver implements ToolCallbackResolver {

    private final Map<String, ToolCallback> byExactName = new LinkedHashMap<>();
    private final McpToolBridge mcpToolBridge;

    public AliasAwareToolCallbackResolver(List<ToolCallback> callbacks) {
        this(callbacks, null);
    }

    public AliasAwareToolCallbackResolver(List<ToolCallback> callbacks, McpToolBridge mcpToolBridge) {
        this.mcpToolBridge = mcpToolBridge;
        for (ToolCallback cb : callbacks) {
            byExactName.put(cb.getToolDefinition().name(), cb);
        }
    }

    @Override
    public ToolCallback resolve(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        ToolCallback direct = byExactName.get(toolName);
        if (direct != null) {
            return direct;
        }
        ToolCallback fromAlias = resolveViaAlias(toolName);
        if (fromAlias != null) {
            return fromAlias;
        }
        ToolCallback live = resolveFromLiveBridge(toolName);
        if (live != null) {
            return live;
        }
        if (McpToolAliasRegistry.isTerminalToolName(toolName)) {
            return new FallbackTerminalToolCallback(toolName);
        }
        return null;
    }

    private ToolCallback resolveViaAlias(String toolName) {
        String canonical = McpToolAliasRegistry.resolveCanonicalName(toolName);
        if (canonical != null) {
            ToolCallback canonicalCb = findByCanonical(canonical);
            if (canonicalCb != null) {
                return canonicalCb;
            }
        }
        for (String alias : McpToolAliasRegistry.aliasesFor(toolName)) {
            ToolCallback aliasCb = byExactName.get(alias);
            if (aliasCb != null) {
                return aliasCb;
            }
        }
        return null;
    }

    private ToolCallback resolveFromLiveBridge(String toolName) {
        if (mcpToolBridge == null || !McpToolAliasRegistry.isTerminalToolName(toolName)) {
            return null;
        }
        ToolCallback shell = findShellCallback(mcpToolBridge.getAllToolCallbacks());
        if (shell == null) {
            return null;
        }
        if (toolName.equals(shell.getToolDefinition().name())) {
            return shell;
        }
        return new AliasedToolCallback(toolName, shell);
    }

    private ToolCallback findShellCallback(List<ToolCallback> callbacks) {
        for (ToolCallback cb : callbacks) {
            String name = cb.getToolDefinition().name();
            if (McpToolAliasRegistry.resolveCanonicalName(name) != null) {
                return cb;
            }
        }
        return null;
    }

    private ToolCallback findByCanonical(String canonical) {
        ToolCallback exact = byExactName.get(canonical);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, ToolCallback> entry : byExactName.entrySet()) {
            if (McpToolAliasRegistry.resolveCanonicalName(entry.getKey()) != null
                    && canonical.equals(McpToolAliasRegistry.resolveCanonicalName(entry.getKey()))) {
                return entry.getValue();
            }
            if (entry.getKey().endsWith("_" + canonical) || entry.getKey().endsWith(canonical)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
