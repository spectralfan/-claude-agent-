package com.kama.jchatmind.mcp.bridge;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 为 MCP 工具注册别名（如 run_terminal_cmd → shell_exec），避免 LLM 按预设名调用时找不到回调。
 */
public class AliasedToolCallback implements ToolCallback {

    private final String aliasName;
    private final ToolCallback delegate;
    private final ToolDefinition aliasDefinition;

    public AliasedToolCallback(String aliasName, ToolCallback delegate) {
        this.aliasName = aliasName;
        this.delegate = delegate;
        ToolDefinition orig = delegate.getToolDefinition();
        String schema = orig.inputSchema();
        if (schema == null || schema.isBlank()) {
            schema = "{\"type\":\"object\",\"properties\":{}}";
        }
        this.aliasDefinition = DefaultToolDefinition.builder()
                .name(aliasName)
                .description(orig.description() + "（MCP 别名: " + orig.name() + "）")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return aliasDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }
}
