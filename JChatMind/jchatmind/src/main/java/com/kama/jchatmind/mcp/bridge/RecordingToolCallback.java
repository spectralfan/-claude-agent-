package com.kama.jchatmind.mcp.bridge;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import com.kama.jchatmind.mcp.model.enums.McpCallStatus;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 在 MCP {@link ToolCallback} 外包一层：委托执行、计时、异步埋点。
 * 工具调用异常不抛出，而是作为工具输出返回，避免中断 Agent 主循环。
 */
public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String serverId;
    private final McpCallRecorder recorder;
    private final boolean recordCalls;
    private final CodingMcpOutputBridge codingMcpOutputBridge;
    private final McpShellArgumentEnricher shellArgumentEnricher;
    private final McpShellCommandPolicy shellCommandPolicy;

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls) {
        this(delegate, serverId, recorder, recordCalls, null, null, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge) {
        this(delegate, serverId, recorder, recordCalls, codingMcpOutputBridge, null, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge,
                                 McpShellArgumentEnricher shellArgumentEnricher) {
        this(delegate, serverId, recorder, recordCalls, codingMcpOutputBridge, shellArgumentEnricher, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge,
                                 McpShellArgumentEnricher shellArgumentEnricher,
                                 McpShellCommandPolicy shellCommandPolicy) {
        this.delegate = delegate;
        this.serverId = serverId;
        this.recorder = recorder;
        this.recordCalls = recordCalls;
        this.codingMcpOutputBridge = codingMcpOutputBridge;
        this.shellArgumentEnricher = shellArgumentEnricher;
        this.shellCommandPolicy = shellCommandPolicy;
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
        String effectiveInput = enrichShellInput(toolName, toolInput);
        long start = System.currentTimeMillis();
        if (shellCommandPolicy != null && McpToolAliasRegistry.isTerminalToolName(toolName)) {
            var blocked = shellCommandPolicy.rejectReason(effectiveInput);
            if (blocked.isPresent()) {
                String msg = blocked.get();
                record(toolName, effectiveInput, msg, null, McpCallStatus.FAILED, start);
                bridgeToCodingTerminal(toolName, effectiveInput, msg);
                return msg;
            }
        }
        try {
            String out = execWithInput(effectiveInput, toolInput, toolContext);
            McpCallStatus status = classifyOutcome(out);
            record(toolName, effectiveInput, out, null, status, start);
            bridgeToCodingTerminal(toolName, effectiveInput, out);
            return out;
        } catch (Exception e) {
            record(toolName, effectiveInput, null, e.getMessage(), McpCallStatus.FAILED, start);
            String err = "MCP 工具[" + toolName + "]调用失败: " + e.getMessage() + "\nexit code: 1";
            bridgeToCodingTerminal(toolName, effectiveInput, err);
            return err;
        }
    }

    private String enrichShellInput(String toolName, String toolInput) {
        if (shellArgumentEnricher == null) {
            return toolInput;
        }
        return shellArgumentEnricher.enrich(toolName, toolInput);
    }

    private String execWithInput(String effectiveInput, String originalInput, ToolContext toolContext) {
        if (effectiveInput.equals(originalInput)) {
            if (toolContext != null) {
                return delegate.call(originalInput, toolContext);
            }
            return delegate.call(originalInput);
        }
        if (toolContext != null) {
            return delegate.call(effectiveInput, toolContext);
        }
        return delegate.call(effectiveInput);
    }

    private static McpCallStatus classifyOutcome(String output) {
        if (output == null) {
            return McpCallStatus.FAILED;
        }
        String lower = output.toLowerCase();
        if (lower.contains("exit code: 1") || lower.contains("调用失败") || lower.contains("iserror")) {
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
        if (!recordCalls || recorder == null) {
            return;
        }
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
