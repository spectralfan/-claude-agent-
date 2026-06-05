package com.kama.jchatmind.event.listener;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingTaskStatus;
import com.kama.jchatmind.coding.service.CodingMessageEnricher;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.memory.integration.MemoryIntegration;
import com.kama.jchatmind.event.ChatEvent;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@AllArgsConstructor
public class ChatEventListener {

    private final JChatMindFactory jChatMindFactory;
    private final CodingTaskService codingTaskService;
    private final CodingMessageEnricher codingMessageEnricher;
    private final MemoryIntegration memoryIntegration;

    @Async
    @EventListener
    public void handle(ChatEvent event) {
        CodingSessionContext.set(event.getSessionId(), event.getAgentId());
        try {
            CodingTask active = codingTaskService.getActiveTask(event.getSessionId());
            if (active != null && CodingTaskStatus.PENDING.getCode().equals(active.getStatus())) {
                codingTaskService.markRunning(active.getId());
            }
            String enrichedInput = event.getUserInput();
            if (active != null && StringUtils.hasText(enrichedInput)) {
                enrichedInput = codingMessageEnricher.enrichUserMessage(enrichedInput, active);
            }
            JChatMind jChatMind = jChatMindFactory.create(
                    event.getAgentId(), event.getSessionId(), enrichedInput);
            jChatMind.run();
        } finally {
            memoryIntegration.onSessionEnd(event.getSessionId());
            CodingSessionContext.clear();
        }
    }
}
