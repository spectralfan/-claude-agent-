package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.mapper.McpToolCallMapper;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * MCP 工具调用埋点：异步写入 t_mcp_tool_call，失败不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpCallRecorder {

    private final McpToolCallMapper mapper;
    private final ObjectMapper objectMapper;

    @Async
    public void record(McpToolCall call) {
        try {
            call.setArguments(toJsonbSafe(call.getArguments()));
            call.setResult(toJsonbSafe(call.getResult()));
            mapper.insert(call);
        } catch (Exception e) {
            log.warn("记录 MCP 工具调用失败 tool={}: {}", call.getToolName(), e.getMessage());
        }
    }

    /**
     * 保证可安全写入 jsonb 列：已是合法 JSON 则原样保留，否则包装为 JSON 字符串字面量。
     */
    private String toJsonbSafe(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            objectMapper.readTree(raw);
            return raw;
        } catch (Exception notJson) {
            try {
                return objectMapper.writeValueAsString(raw);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
