package com.kama.jchatmind.memory.integration;

import com.kama.jchatmind.memory.model.dto.ToolCallInfo;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Memory Hub 与 Think-Execute 主流程的集成点。
 */
public interface MemoryIntegration {

    /**
     * 构建运行时上下文消息（供评测或全量 Hub 加载）。
     */
    List<Message> buildContext(String sessionId, int maxTokens);

    /**
     * 构建 RECENT/ARCHIVE 补充记忆，与 chat_message 主链路并存。
     */
    List<Message> buildSupplementalContext(String sessionId, int maxTokens);

    /**
     * 工具执行后记录到记忆。
     */
    void onToolExecuted(String sessionId, ToolCallInfo toolCall);

    /**
     * 用户确认后记录到记忆（高重要性）。
     */
    void onUserConfirmed(String sessionId, String confirmation);

    /**
     * 对话结束时触发记忆整理。
     */
    void onSessionEnd(String sessionId);
}
