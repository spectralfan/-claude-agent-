package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.service.CodingSubtaskExecutor;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.coding.service.OrchestratorContinuationService;
import com.kama.jchatmind.memory.integration.MemoryIntegration;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.ChatSessionProvisioner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CodingSubtaskExecutorImpl implements CodingSubtaskExecutor {

    private final JChatMindFactory jChatMindFactory;
    private final CodingSubtaskService codingSubtaskService;
    private final RealtimeNotifier realtimeNotifier;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final MemoryIntegration memoryIntegration;
    private final ChatSessionProvisioner chatSessionProvisioner;
    private final OrchestratorContinuationService orchestratorContinuationService;

    public CodingSubtaskExecutorImpl(
            @Lazy JChatMindFactory jChatMindFactory,
            CodingSubtaskService codingSubtaskService,
            RealtimeNotifier realtimeNotifier,
            ChatMessageFacadeService chatMessageFacadeService,
            MemoryIntegration memoryIntegration,
            ChatSessionProvisioner chatSessionProvisioner,
            OrchestratorContinuationService orchestratorContinuationService) {
        this.jChatMindFactory = jChatMindFactory;
        this.codingSubtaskService = codingSubtaskService;
        this.realtimeNotifier = realtimeNotifier;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.memoryIntegration = memoryIntegration;
        this.chatSessionProvisioner = chatSessionProvisioner;
        this.orchestratorContinuationService = orchestratorContinuationService;
    }

    @Override
    @Async("codingExecutor")
    public void execute(CodingSubtaskDTO subtask) {
        String parentSessionId = subtask.getParentSessionId();
        // 子 Agent 独立 chat_message 会话：须为合法 UUID（Mapper 中 CAST session_id AS uuid）
        String subSessionId = subtask.getId();

        SubAgentRunContext.set(parentSessionId, subtask.getId(), subtask.getTitle());
        CodingSessionContext.set(parentSessionId, subtask.getWorkerAgentId());
        codingSubtaskService.markRunning(subtask.getId());
        publishEvent(parentSessionId, subtask, SseMessage.Type.CODING_SUBTASK_STARTED, null, null);

        try {
            provisionWorkerSession(subtask, subSessionId);
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
            triggerOrchestratorContinue(subtask);
        } catch (Exception e) {
            log.error("子任务失败: {} ({})", subtask.getTitle(), subtask.getId(), e);
            String error = formatFailureMessage(e);
            codingSubtaskService.markFailed(subtask.getId(), error);
            publishEvent(parentSessionId, subtask, SseMessage.Type.CODING_SUBTASK_FAILED, null, error);
            triggerOrchestratorContinue(codingSubtaskService.findById(subtask.getId()).orElse(subtask));
        } finally {
            memoryIntegration.onSessionEnd(subSessionId);
            SubAgentRunContext.clear();
            CodingSessionContext.clear();
        }
    }

    private void triggerOrchestratorContinue(CodingSubtaskDTO subtask) {
        if (subtask != null) {
            orchestratorContinuationService.onSubtaskFinished(subtask);
        }
    }

    private void provisionWorkerSession(CodingSubtaskDTO subtask, String subSessionId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "coding_subtask");
        metadata.put("hidden", true);
        metadata.put("parentSessionId", subtask.getParentSessionId());
        metadata.put("parentTaskId", subtask.getParentTaskId());
        metadata.put("subTaskId", subtask.getId());
        chatSessionProvisioner.ensureSession(
                subSessionId,
                subtask.getWorkerAgentId(),
                "Worker: " + subtask.getTitle(),
                metadata
        );
    }

    private static String formatFailureMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        if (detail != null && !detail.isBlank()) {
            return "Error running agent: " + detail;
        }
        return "Error running agent: " + root.getClass().getSimpleName();
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
