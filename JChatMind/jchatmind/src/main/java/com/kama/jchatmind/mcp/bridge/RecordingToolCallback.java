package com.kama.jchatmind.mcp.bridge;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import com.kama.jchatmind.mcp.model.enums.McpCallStatus;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.function.Supplier;

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

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls) {
        this(delegate, serverId, recorder, recordCalls, null);
    }

    public RecordingToolCallback(ToolCallback delegate, String serverId,
                                 McpCallRecorder recorder, boolean recordCalls,
                                 CodingMcpOutputBridge codingMcpOutputBridge) {
        this.delegate = delegate;
        this.serverId = serverId;
        this.recorder = recorder;
        this.recordCalls = recordCalls;
        this.codingMcpOutputBridge = codingMcpOutputBridge;
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
        return invoke(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String invoke(String toolInput, Supplier<String> exec) {
        String toolName = delegate.getToolDefinition().name();
        long start = System.currentTimeMillis();
        try {
            String out = exec.get();
            record(toolName, toolInput, out, null, McpCallStatus.SUCCESS, start);
            bridgeToCodingTerminal(toolName, toolInput, out);
            return out;
        } catch (Exception e) {
            record(toolName, toolInput, null, e.getMessage(), McpCallStatus.FAILED, start);
            String err = "MCP 工具[" + toolName + "]调用失败: " + e.getMessage();
            bridgeToCodingTerminal(toolName, toolInput, err);
            return err;
        }
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
