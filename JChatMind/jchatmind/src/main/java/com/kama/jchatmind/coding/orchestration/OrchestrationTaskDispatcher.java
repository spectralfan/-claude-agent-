package com.kama.jchatmind.coding.orchestration;

import com.kama.jchatmind.coding.config.OrchestrationProperties;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskStatus;
import com.kama.jchatmind.coding.service.OrchestrationTaskExecutor;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.coding.service.CodingReviewerPresetService;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrchestrationTaskDispatcher {

    private final OrchestrationProperties orchestrationProperties;
    private final OrchestrationTaskService orchestrationTaskService;
    private final OrchestrationTaskExecutor orchestrationTaskExecutor;
    private final CodingAgentPresetService codingAgentPresetService;
    private final CodingReviewerPresetService codingReviewerPresetService;

    public OrchestrationTaskDispatcher(
            OrchestrationProperties orchestrationProperties,
            OrchestrationTaskService orchestrationTaskService,
            OrchestrationTaskExecutor orchestrationTaskExecutor,
            CodingAgentPresetService codingAgentPresetService,
            CodingReviewerPresetService codingReviewerPresetService) {
        this.orchestrationProperties = orchestrationProperties;
        this.orchestrationTaskService = orchestrationTaskService;
        this.orchestrationTaskExecutor = orchestrationTaskExecutor;
        this.codingAgentPresetService = codingAgentPresetService;
        this.codingReviewerPresetService = codingReviewerPresetService;
    }

    private final Set<String> dirtySessions = ConcurrentHashMap.newKeySet();
    private final Set<String> dispatchingTasks = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onGraphChanged(OrchestrationGraphChangedEvent event) {
        notifyGraphChanged(event.parentSessionId());
    }

    @EventListener
    public void onTaskFinishedEvent(OrchestrationTaskFinishedEvent event) {
        onTaskFinished(event.task());
    }

    public void notifyGraphChanged(String parentSessionId) {
        if (parentSessionId != null) {
            dirtySessions.add(parentSessionId);
        }
    }

    public void onTaskFinished(OrchestrationTaskDTO finished) {
        if (finished == null) {
            return;
        }
        maybeSpawnReviewer(finished);
        notifyGraphChanged(finished.getParentSessionId());
    }

    @Scheduled(fixedDelayString = "${coding.orchestration.dispatch-interval-ms:2000}")
    public void dispatchReadyTasks() {
        Set<String> sessions = Set.copyOf(dirtySessions);
        dirtySessions.clear();
        if (sessions.isEmpty()) {
            return;
        }
        for (String sessionId : sessions) {
            tryDispatchSession(sessionId);
        }
    }

    private void tryDispatchSession(String parentSessionId) {
        int running = orchestrationTaskService.countRunning(parentSessionId);
        int slots = orchestrationProperties.getMaxConcurrency() - running;
        if (slots <= 0) {
            dirtySessions.add(parentSessionId);
            return;
        }
        List<OrchestrationTaskDTO> ready = orchestrationTaskService.listReadyByParentSession(parentSessionId);
        for (OrchestrationTaskDTO task : ready) {
            if (slots <= 0) {
                dirtySessions.add(parentSessionId);
                break;
            }
            if (!dispatchingTasks.add(task.getId())) {
                continue;
            }
            slots--;
            orchestrationTaskExecutor.execute(task, () -> dispatchingTasks.remove(task.getId()));
        }
    }

    private void maybeSpawnReviewer(OrchestrationTaskDTO finished) {
        if (!orchestrationProperties.isAutoReview()) {
            return;
        }
        if (!OrchestrationTaskStatus.COMPLETED.getCode().equals(finished.getStatus())) {
            return;
        }
        if (!OrchestrationTaskRole.WORKER.getCode().equals(finished.getRole())) {
            return;
        }
        Map<String, Object> meta = finished.getMetadata() != null ? finished.getMetadata() : Map.of();
        if (Boolean.TRUE.equals(meta.get("skipReview"))) {
            return;
        }
        String reviewerAgentId = resolveReviewerAgentId();
        if (reviewerAgentId == null) {
            log.warn("未找到 Reviewer Agent，跳过自动审查 task={}", finished.getId());
            return;
        }
        List<String> deps = orchestrationProperties.isAutoReviewDepends()
                ? List.of(finished.getId())
                : List.of();
        String goal = """
                复查 Worker 任务「%s」的产出与风险。
                任务目标：%s
                Worker 摘要：%s
                请检查：代码质量、遗漏测试、安全风险、与约束一致性。输出审查结论（通过/需修复及原因）。
                """.formatted(
                finished.getTitle(),
                finished.getGoal(),
                finished.getResultSummary() != null ? finished.getResultSummary() : "(无摘要)"
        );
        Map<String, Object> reviewMeta = new LinkedHashMap<>();
        reviewMeta.put("autoReview", true);
        reviewMeta.put("sourceWorkerTaskId", finished.getId());

        orchestrationTaskService.create(
                finished.getParentSessionId(),
                finished.getParentTaskId(),
                OrchestrationTaskRole.REVIEWER,
                "Review: " + finished.getTitle(),
                goal.trim(),
                finished.getConstraints(),
                finished.getContextFiles(),
                deps,
                reviewerAgentId,
                finished.getDepth(),
                finished.getId(),
                reviewMeta
        );
        log.info("已自动创建 Reviewer 任务，来源 Worker={}", finished.getId());
    }

    private String resolveReviewerAgentId() {
        return codingReviewerPresetService.findPreset()
                .map(p -> p.getAgentId())
                .orElse(null);
    }

    String resolveWorkerAgentId() {
        return codingAgentPresetService.findPreset()
                .map(p -> p.getAgentId())
                .orElse(null);
    }
}
