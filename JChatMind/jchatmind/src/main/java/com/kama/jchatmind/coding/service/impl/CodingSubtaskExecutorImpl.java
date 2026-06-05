package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.service.CodingSubtaskExecutor;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CodingSubtaskExecutorImpl implements CodingSubtaskExecutor {

    private final JChatMindFactory jChatMindFactory;
    private final CodingSubtaskService codingSubtaskService;
    private final RealtimeNotifier realtimeNotifier;
    private final ChatMessageFacadeService chatMessageFacadeService;

    public CodingSubtaskExecutorImpl(
            @Lazy JChatMindFactory jChatMindFactory,
            CodingSubtaskService codingSubtaskService,
            RealtimeNotifier realtimeNotifier,
            ChatMessageFacadeService chatMessageFacadeService) {
        this.jChatMindFactory = jChatMindFactory;
        this.codingSubtaskService = codingSubtaskService;
        this.realtimeNotifier = realtimeNotifier;
        this.chatMessageFacadeService = chatMessageFacadeService;
    }

    @Override
    @Async("taskExecutor")
    public void execute(CodingSubtaskDTO subtask) {
        String parentSessionId = subtask.getParentSessionId();
        String subSessionId = parentSessionId + "#sub#" + subtask.getId();

        SubAgentRunContext.set(parentSessionId, subtask.getId(), subtask.getTitle());
        CodingSessionContext.set(parentSessionId, subtask.getWorkerAgentId());
        codingSubtaskService.markRunning(subtask.getId());
        publishEvent(parentSessionId, subtask, SseMessage.Type.CODING_SUBTASK_STARTED, null, null);

        try {
            JChatMind worker = jChatMindFactory.createSubAgent(
                    subtask.getWorkerAgentId(),
                    parentSessionId,
                    subSessionId,
                    subtask.getGoal()
            );
            worker.run();
            String summary = extractSummary(subSessionId);
            codingSubtaskService.markCompleted(subtask.getId(), summary);
            publishEvent(parentSessionId, subtask, SseMessage.Type.CODING_SUBTASK_COMPLETED, summary, null);
            log.info("子任务完成: {} ({})", subtask.getTitle(), subtask.getId());
        } catch (Exception e) {
            log.error("子任务失败: {} ({})", subtask.getTitle(), subtask.getId(), e);
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            codingSubtaskService.markFailed(subtask.getId(), error);
            publishEvent(parentSessionId, subtask, SseMessage.Type.CODING_SUBTASK_FAILED, null, error);
        } finally {
            SubAgentRunContext.clear();
            CodingSessionContext.clear();
        }
    }

    private String extractSummary(String subSessionId) {
        List<ChatMessageDTO> messages =
                chatMessageFacadeService.getChatMessagesBySessionIdRecently(subSessionId, 20);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDTO msg = messages.get(i);
            if (msg.getRole() == ChatMessageDTO.RoleType.ASSISTANT
                    && msg.getContent() != null
                    && !msg.getContent().isBlank()) {
                return msg.getContent().trim();
            }
        }
        return "子 Agent 执行结束，请查看工作区变更或通过 list_coding_subtasks 获取详情。";
    }

    private void publishEvent(String parentSessionId, CodingSubtaskDTO subtask,
                              SseMessage.Type type, String summary, String error) {
        realtimeNotifier.tryPublish(parentSessionId, SseMessage.builder()
                    .type(type)
                    .payload(SseMessage.Payload.builder()
                            .taskId(subtask.getParentTaskId())
                            .subTaskId(subtask.getId())
                            .statusText(subtask.getTitle())
                            .detail(subtask.getGoal())
                            .summary(summary)
                            .output(error)
                            .done(type == SseMessage.Type.CODING_SUBTASK_COMPLETED
                                    || type == SseMessage.Type.CODING_SUBTASK_FAILED)
                            .build())
                    .build());
    }
}
