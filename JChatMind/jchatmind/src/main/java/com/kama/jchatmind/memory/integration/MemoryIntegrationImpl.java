package com.kama.jchatmind.memory.integration;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.dto.ToolCallInfo;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.MemoryService;
import com.kama.jchatmind.memory.service.MemorySelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryIntegrationImpl implements MemoryIntegration {

    /** 用户确认类消息的重要性 */
    private static final int CONFIRMATION_IMPORTANCE = 7;

    private final MemoryService memoryService;
    private final MemorySelector memorySelector;
    private final MemoryProperties memoryProperties;

    @Override
    public List<Message> buildContext(String sessionId, int maxTokens) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        return memoryService.buildContextMessages(sessionId, maxTokens);
    }

    @Override
    public List<Message> buildSupplementalContext(String sessionId, int maxTokens) {
        if (!memoryProperties.isEnabled()) {
            return List.of();
        }
        return memoryService.buildSupplementalMessages(sessionId, maxTokens);
    }

    @Override
    public void onToolExecuted(String sessionId, ToolCallInfo toolCall) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        if (toolCall == null) {
            return;
        }
        String content = StringUtils.hasText(toolCall.getResult())
                ? toolCall.getResult()
                : ("调用工具 " + toolCall.getName());
        MemorySaveDTO dto = MemorySaveDTO.builder()
                .sessionId(sessionId)
                .role(MemoryRole.TOOL)
                .memoryType(MemoryType.WORKING)
                .content(content)
                .toolCalls(List.of(toolCall))
                .metadata(Map.of("tool", toolCall.getName() == null ? "" : toolCall.getName()))
                .build();
        memoryService.save(dto);
    }

    @Override
    public void onUserConfirmed(String sessionId, String confirmation) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(confirmation)) {
            return;
        }
        MemorySaveDTO dto = MemorySaveDTO.builder()
                .sessionId(sessionId)
                .role(MemoryRole.USER)
                .memoryType(MemoryType.WORKING)
                .content(confirmation)
                .importance(CONFIRMATION_IMPORTANCE)
                .metadata(Map.of("kind", "confirmation"))
                .build();
        memoryService.save(dto);
    }

    @Override
    public void onSessionEnd(String sessionId) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        log.info("会话结束，触发记忆整理 session={}", sessionId);
        memoryService.updateSessionActivity(sessionId);
        try {
            memorySelector.adjustTiers(sessionId);
        } catch (Exception e) {
            log.warn("Memory Hub 层级调整失败 session={}: {}", sessionId, e.getMessage());
        }
        memoryService.triggerConsolidation(sessionId);
    }
}
