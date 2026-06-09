package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.CodingAgentRoles;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskStatus;
import com.kama.jchatmind.coding.service.CodingTaskAutoProvisioner;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.coding.service.OrchestratorContinuationService;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrchestratorContinuationServiceImpl implements OrchestratorContinuationService {

    private final CodingSubagentProperties subagentProperties;
    private final OrchestrationTaskService orchestrationTaskService;
    private final CodingTaskService codingTaskService;
    private final CodingTaskAutoProvisioner taskAutoProvisioner;
    private final JChatMindFactory jChatMindFactory;
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;

    private final Map<String, AtomicInteger> continuationCounts = new ConcurrentHashMap<>();

    public OrchestratorContinuationServiceImpl(
            CodingSubagentProperties subagentProperties,
            OrchestrationTaskService orchestrationTaskService,
            CodingTaskService codingTaskService,
            CodingTaskAutoProvisioner taskAutoProvisioner,
            @Lazy JChatMindFactory jChatMindFactory,
            AgentMapper agentMapper,
            AgentConverter agentConverter) {
        this.subagentProperties = subagentProperties;
        this.orchestrationTaskService = orchestrationTaskService;
        this.codingTaskService = codingTaskService;
        this.taskAutoProvisioner = taskAutoProvisioner;
        this.jChatMindFactory = jChatMindFactory;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
    }

    @Override
    @Async("codingExecutor")
    public void onOrchestrationTaskFinished(OrchestrationTaskDTO task) {
        if (!subagentProperties.isEnabled() || !subagentProperties.isAutoContinue()) {
            return;
        }
        String parentSessionId = task.getParentSessionId();
        CodingTask codingTask = codingTaskService.getActiveTask(parentSessionId);
        if (codingTask == null) {
            return;
        }
        if (!isSchedulerAgent(codingTask.getAgentId())) {
            return;
        }
        if (hasActiveOrchestrationTasks(parentSessionId)) {
            log.debug("仍有进行中的编排任务，暂不自动继续 session={}", parentSessionId);
            return;
        }
        if (!orchestrationTaskService.allTerminal(parentSessionId)) {
            return;
        }
        int count = continuationCounts
                .computeIfAbsent(parentSessionId, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > subagentProperties.getMaxAutoContinuations()) {
            log.warn("已达自动继续上限 session={} count={}", parentSessionId, count);
            return;
        }

        String prompt = buildOrchestrationContinuationPrompt(task);
        CodingSessionContext.set(parentSessionId, codingTask.getAgentId());
        try {
            taskAutoProvisioner.ensureActiveTask(parentSessionId, codingTask.getAgentId());
            JChatMind scheduler = jChatMindFactory.createContinuation(
                    codingTask.getAgentId(), parentSessionId, prompt);
            log.info("编排任务 {} 完成后自动继续 Scheduler (#{})", task.getId(), count);
            scheduler.run();
        } catch (Exception e) {
            log.error("自动继续 Scheduler 失败 session={}: {}", parentSessionId, e.getMessage(), e);
        } finally {
            CodingSessionContext.clear();
        }
    }

    @Override
    @Async("codingExecutor")
    public void onSubtaskFinished(CodingSubtaskDTO subtask) {
        orchestrationTaskService.findById(subtask.getId())
                .ifPresent(this::onOrchestrationTaskFinished);
    }

    private boolean hasActiveOrchestrationTasks(String parentSessionId) {
        return orchestrationTaskService.listByParentSession(parentSessionId).stream()
                .anyMatch(t -> {
                    OrchestrationTaskStatus status = OrchestrationTaskStatus.fromCode(t.getStatus());
                    return status == OrchestrationTaskStatus.RUNNING
                            || status == OrchestrationTaskStatus.PENDING
                            || status == OrchestrationTaskStatus.READY;
                });
    }

    private boolean isSchedulerAgent(String agentId) {
        try {
            Agent agent = agentMapper.selectById(agentId);
            if (agent == null) {
                return false;
            }
            return CodingAgentRoles.isOrchestrator(agentConverter.toDTO(agent));
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildOrchestrationContinuationPrompt(OrchestrationTaskDTO task) {
        String summary = task.getResultSummary() != null
                ? task.getResultSummary()
                : "(无摘要)";
        String status = task.getStatus();
        return """
                [系统自动继续] 编排任务「%s」role=%s status=%s。
                结果摘要：%s
                
                请继续调度直至用户原始需求全部完成：
                1. 用 list_orchestration_tasks 查看 DAG 进度
                2. 若仍有未完成部分，继续 create_orchestration_task 创建 Worker/Reviewer
                3. 全部终态后向用户汇总交付说明；最终调用 mark_coding_complete
                不要停下来等待用户再发消息。
                """.formatted(task.getTitle(), task.getRole(), status, summary);
    }
}
