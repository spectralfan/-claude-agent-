package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskSpec;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.service.OrchestrationTaskExecutor;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
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
public class OrchestrationTaskExecutorImpl implements OrchestrationTaskExecutor {

    private final JChatMindFactory jChatMindFactory;
    private final OrchestrationTaskService orchestrationTaskService;
    private final RealtimeNotifier realtimeNotifier;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final MemoryIntegration memoryIntegration;
    private final ChatSessionProvisioner chatSessionProvisioner;
    private final OrchestratorContinuationService orchestratorContinuationService;

    public OrchestrationTaskExecutorImpl(
            @Lazy JChatMindFactory jChatMindFactory,
            OrchestrationTaskService orchestrationTaskService,
            RealtimeNotifier realtimeNotifier,
            ChatMessageFacadeService chatMessageFacadeService,
            MemoryIntegration memoryIntegration,
            ChatSessionProvisioner chatSessionProvisioner,
            OrchestratorContinuationService orchestratorContinuationService) {
        this.jChatMindFactory = jChatMindFactory;
        this.orchestrationTaskService = orchestrationTaskService;
        this.realtimeNotifier = realtimeNotifier;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.memoryIntegration = memoryIntegration;
        this.chatSessionProvisioner = chatSessionProvisioner;
        this.orchestratorContinuationService = orchestratorContinuationService;
    }

    @Override
    @Async("codingExecutor")
    public void execute(OrchestrationTaskDTO task, Runnable onFinally) {
        String parentSessionId = task.getParentSessionId();
        String subSessionId = task.getId();

        SubAgentRunContext.set(parentSessionId, task.getId(), task.getTitle());
        CodingSessionContext.set(parentSessionId, task.getWorkerAgentId());
        orchestrationTaskService.markRunning(task.getId());
        publishEvent(parentSessionId, task, SseMessage.Type.CODING_SUBTASK_STARTED, null, null);

        try {
            provisionSubSession(task, subSessionId);
            OrchestrationTaskSpec spec = OrchestrationTaskSpec.builder()
                    .taskId(task.getId())
                    .parentSessionId(parentSessionId)
                    .parentCodingTaskId(task.getParentTaskId())
                    .role(OrchestrationTaskRole.fromCode(task.getRole()))
                    .title(task.getTitle())
                    .goal(task.getGoal())
                    .constraints(task.getConstraints())
                    .contextFiles(task.getContextFiles())
                    .agentId(task.getWorkerAgentId())
                    .depth(task.getDepth())
                    .build();

            JChatMind roleAgent = jChatMindFactory.createRoleAgent(spec, subSessionId);
            roleAgent.run();

            String summary = extractSummary(subSessionId);
            orchestrationTaskService.markCompleted(task.getId(), summary);
            publishEvent(parentSessionId, task, SseMessage.Type.CODING_SUBTASK_COMPLETED, summary, null);
            log.info("编排任务完成: {} [{}] ({})", task.getTitle(), task.getRole(), task.getId());
            orchestrationTaskService.findById(task.getId()).ifPresent(this::triggerSchedulerContinue);
        } catch (Exception e) {
            log.error("编排任务失败: {} [{}] ({})", task.getTitle(), task.getRole(), task.getId(), e);
            String error = formatFailureMessage(e);
            orchestrationTaskService.markFailed(task.getId(), error);
            publishEvent(parentSessionId, task, SseMessage.Type.CODING_SUBTASK_FAILED, null, error);
            orchestrationTaskService.findById(task.getId()).ifPresent(this::triggerSchedulerContinue);
        } finally {
            memoryIntegration.onSessionEnd(subSessionId);
            SubAgentRunContext.clear();
            CodingSessionContext.clear();
            if (onFinally != null) {
                onFinally.run();
            }
        }
    }

    private void triggerSchedulerContinue(OrchestrationTaskDTO finished) {
        if (finished == null) {
            return;
        }
        orchestratorContinuationService.onOrchestrationTaskFinished(finished);
    }

    private void provisionSubSession(OrchestrationTaskDTO task, String subSessionId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", "orchestration_task");
        metadata.put("hidden", true);
        metadata.put("parentSessionId", task.getParentSessionId());
        metadata.put("parentTaskId", task.getParentTaskId());
        metadata.put("orchestrationTaskId", task.getId());
        metadata.put("role", task.getRole());
        chatSessionProvisioner.ensureSession(
                subSessionId,
                task.getWorkerAgentId(),
                task.getRole() + ": " + task.getTitle(),
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
        return "子 Agent 执行结束，请通过 list_orchestration_tasks 查看详情。";
    }

    private void publishEvent(String parentSessionId, OrchestrationTaskDTO task,
                              SseMessage.Type type, String summary, String error) {
        String deps = task.getDependsOn() != null && !task.getDependsOn().isEmpty()
                ? String.join(",", task.getDependsOn())
                : null;
        realtimeNotifier.tryPublish(parentSessionId, SseMessage.builder()
                .type(type)
                .payload(SseMessage.Payload.builder()
                        .taskId(task.getParentTaskId())
                        .subTaskId(task.getId())
                        .role(task.getRole())
                        .dependsOn(deps)
                        .statusText(task.getTitle())
                        .detail(task.getGoal())
                        .summary(summary)
                        .output(error)
                        .done(type == SseMessage.Type.CODING_SUBTASK_COMPLETED
                                || type == SseMessage.Type.CODING_SUBTASK_FAILED)
                        .build())
                .build());
    }
}
