package com.kama.jchatmind.coding.bridge;

import com.kama.jchatmind.mcp.permission.PermissionManager;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.UUID;

/**
 * 权限感知工具回调包装器 — 为非 MCP 的 Spring @Component 工具添加权限审批。
 * 对齐 KamaClaude 的 invoke_tool() 权限检查。
 */
public class PermissionAwareToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final PermissionManager permissionManager;

    public PermissionAwareToolCallback(ToolCallback delegate, PermissionManager permissionManager) {
        this.delegate = delegate;
        this.permissionManager = permissionManager;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return checkThenDelegate(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return checkThenDelegate(toolInput, toolContext);
    }

    private String checkThenDelegate(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        String toolUseId = UUID.randomUUID().toString();
        String paramPreview = toolInput.length() > 120
                ? toolInput.substring(0, 120) + "..."
                : toolInput;

        boolean allowed = permissionManager.requestApproval(
                toolUseId, toolName, toolInput, paramPreview);

        if (!allowed) {
            return "权限被拒绝: " + toolName + "\nexit code: 1";
        }

        if (toolContext != null) {
            return delegate.call(toolInput, toolContext);
        }
        return delegate.call(toolInput);
    }
}