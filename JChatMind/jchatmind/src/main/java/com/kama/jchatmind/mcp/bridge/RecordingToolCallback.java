package com.kama.jchatmind.mcp.bridge;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import com.kama.jchatmind.mcp.model.enums.McpCallStatus;
import com.kama.jchatmind.mcp.permission.PermissionManager;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.UUID;

public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String serverId;
    private final McpCallRecorder recorder;
    private final boolean recordCalls;
    private final CodingMcpOutputBridge codingMcpOutputBridge;
    private final PermissionManager permissionManager;

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls) {
        this(delegate, serverId, recorder, recordCalls, null, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge) {
        this(delegate, serverId, recorder, recordCalls, codingMcpOutputBridge, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge,
                                 PermissionManager permissionManager) {
        this.delegate = delegate;
        this.serverId = serverId;
        this.recorder = recorder;
        this.recordCalls = recordCalls;
        this.codingMcpOutputBridge = codingMcpOutputBridge;
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
        return invoke(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(toolInput, toolContext);
    }

    private String invoke(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();

        // 权限审批
        if (permissionManager != null) {
            String toolUseId = UUID.randomUUID().toString();
            String paramPreview = toolInput.length() > 120
                    ? toolInput.substring(0, 120) + "..."
                    : toolInput;
            boolean allowed = permissionManager.requestApproval(toolUseId, toolName, toolInput, paramPreview);
            if (!allowed) {
                String err = "MCP 工具[" + toolName + "]调用被拒绝";
                record(toolName, toolInput, null, err, McpCallStatus.FAILED, start);
                return err + "\nexit code: 1";
            }
        }

        try {
            String out = toolContext != null
                    ? delegate.call(toolInput, toolContext)
                    : delegate.call(toolInput);
            McpCallStatus status = classifyOutcome(out);
            record(toolName, toolInput, out, null, status, start);
            bridgeToCodingTerminal(toolName, toolInput, out);
            return out;
        } catch (Exception e) {
            String err = "MCP 工具[" + toolName + "]调用失败: " + e.getMessage() + "\nexit code: 1";
            record(toolName, toolInput, null, e.getMessage(), McpCallStatus.FAILED, start);
            bridgeToCodingTerminal(toolName, toolInput, err);
            return err;
        }
    }

    private static McpCallStatus classifyOutcome(String output) {
        if (output == null) return McpCallStatus.FAILED;
        String lower = output.toLowerCase();
        if (lower.contains("exit code: 1") || lower.contains("iserror")) {
            return McpCallStatus.FAILED;
        }
        return McpCallStatus.SUCCESS;
    }

    private void bridgeToCodingTerminal(String toolName, String toolInput, String output) {
        if (codingMcpOutputBridge != null) {
            codingMcpOutputBridge.onToolResult(toolName, toolInput, output);
        }
    }

    private void record(String toolName, String arguments, String result,
                        String errorMessage, McpCallStatus status, long startMillis) {
        if (!recordCalls || recorder == null) return;
        McpToolCall call = McpToolCall.builder()
                .serverId(serverId)
                .toolName(toolName)
                .arguments(arguments)
                .result(result)
                .errorMessage(errorMessage)
                .status(status.getCode())
                .durationMs((int) (System.currentTimeMillis() - startMillis))
                .build();
        recorder.record(call);
    }
}