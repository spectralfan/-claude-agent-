package com.kama.jchatmind.memory.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 工具调用信息载体。
 * 项目原本直接使用 Spring AI 的 {@link AssistantMessage.ToolCall}，
 * 这里封装为 Memory Hub 自有 DTO，便于记忆持久化与重要性评估解耦。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallInfo {

    private String id;

    private String name;

    /** 工具调用入参（一般为 JSON 字符串） */
    private String arguments;

    /** 工具执行结果（可选） */
    private String result;

    public static ToolCallInfo from(AssistantMessage.ToolCall toolCall) {
        if (toolCall == null) {
            return null;
        }
        return ToolCallInfo.builder()
                .id(toolCall.id())
                .name(toolCall.name())
                .arguments(toolCall.arguments())
                .build();
    }
}
