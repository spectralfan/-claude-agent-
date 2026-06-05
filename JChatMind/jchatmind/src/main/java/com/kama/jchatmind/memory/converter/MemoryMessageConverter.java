package com.kama.jchatmind.memory.converter;

import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 将记忆条目转换为 Spring AI 的对话消息。
 *
 * <p>注意：被召回/融合的记忆来自不同层级，无法保证 tool_calls 与 tool 响应严格相邻，
 * 因此这里采用安全策略——assistant 仅保留文本（丢弃 toolCalls），tool 条目渲染为
 * SystemMessage 文本说明，避免触发模型「tool 必须紧跟带 tool_calls 的 assistant」约束。</p>
 */
@Component
public class MemoryMessageConverter {

    public List<Message> toMessages(List<MemoryEntry> entries) {
        List<Message> messages = new ArrayList<>();
        if (entries == null) {
            return messages;
        }
        for (MemoryEntry entry : entries) {
            Message message = toMessage(entry);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    public Message toMessage(MemoryEntry entry) {
        if (entry == null) {
            return null;
        }
        // 归档记忆优先使用摘要
        String text = StringUtils.hasText(entry.getSummary()) ? entry.getSummary() : entry.getContent();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        MemoryRole role = MemoryRole.fromCode(entry.getRole());
        return switch (role) {
            case USER -> new UserMessage(text);
            case ASSISTANT -> new AssistantMessage(text);
            case SYSTEM -> new SystemMessage(text);
            case TOOL -> new SystemMessage("[历史工具结果] " + text);
        };
    }
}
