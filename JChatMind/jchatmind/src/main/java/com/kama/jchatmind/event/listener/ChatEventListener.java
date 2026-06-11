package com.kama.jchatmind.event.listener;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingTaskStatus;
import com.kama.jchatmind.coding.service.CodingMessageEnricher;
import com.kama.jchatmind.coding.service.CodingTaskAutoProvisioner;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.memory.integration.MemoryIntegration;
import com.kama.jchatmind.event.ChatEvent;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.RealtimeNotifier;
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
    private final CodingTaskAutoProvisioner codingTaskAutoProvisioner;
    private final CodingMessageEnricher codingMessageEnricher;
    private final MemoryIntegration memoryIntegration;
    private final RealtimeNotifier realtimeNotifier;

    @Async
    @EventListener
    public void handle(ChatEvent event) {
        CodingSessionContext.set(event.getSessionId(), event.getAgentId());
        try {
            CodingTask active = codingTaskAutoProvisioner.ensureActiveTask(
                    event.getSessionId(), event.getAgentId());
            if (active != null) {
                boolean stackDetected = codingTaskService.applyDetectedStackIfAbsent(active);
                if (stackDetected) {
                    CodingTaskMetadata meta = CodingTaskMetadata.fromJson(active.getMetadata());
                    realtimeNotifier.tryPublish(event.getSessionId(), SseMessage.builder()
                            .type(SseMessage.Type.CODING_STACK_DETECTED)
                            .payload(SseMessage.Payload.builder()
                                    .taskId(active.getId())
                                    .stackId(meta.getStackId())
                                    .detail(meta.getLanguage())
                                    .statusText("已自动识别技术栈: " + meta.getStackId())
                                    .build())
                            .build());
                }
                if (CodingTaskStatus.PENDING.getCode().equals(active.getStatus())) {
                    codingTaskService.markRunning(active.getId());
                }
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
