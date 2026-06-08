package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.coding.CodingAgentRoles;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingSubtaskStatus;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.coding.service.CodingTaskAutoProvisioner;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestratorContinuationService;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrchestratorContinuationServiceImpl implements OrchestratorContinuationService {

    private final CodingSubagentProperties subagentProperties;
    private final CodingSubtaskService codingSubtaskService;
    private final CodingTaskService codingTaskService;
    private final CodingTaskAutoProvisioner taskAutoProvisioner;
    private final JChatMindFactory jChatMindFactory;
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;

    private final Map<String, AtomicInteger> continuationCounts = new ConcurrentHashMap<>();

    public OrchestratorContinuationServiceImpl(
            CodingSubagentProperties subagentProperties,
            CodingSubtaskService codingSubtaskService,
            CodingTaskService codingTaskService,
            CodingTaskAutoProvisioner taskAutoProvisioner,
            @Lazy JChatMindFactory jChatMindFactory,
            AgentMapper agentMapper,
            AgentConverter agentConverter) {
        this.subagentProperties = subagentProperties;
        this.codingSubtaskService = codingSubtaskService;
        this.codingTaskService = codingTaskService;
        this.taskAutoProvisioner = taskAutoProvisioner;
        this.jChatMindFactory = jChatMindFactory;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
    }

    @Override
    @Async("codingExecutor")
    public void onSubtaskFinished(CodingSubtaskDTO subtask) {
        if (!subagentProperties.isEnabled() || !subagentProperties.isAutoContinue()) {
            return;
        }
        String parentSessionId = subtask.getParentSessionId();
        CodingTask task = codingTaskService.getActiveTask(parentSessionId);
        if (task == null) {
            return;
        }
        if (!isOrchestratorAgent(task.getAgentId())) {
            return;
        }
        if (hasRunningSubtasks(parentSessionId)) {
            log.debug("仍有进行中的子任务，暂不自动继续编排 session={}", parentSessionId);
            return;
        }
        int count = continuationCounts
                .computeIfAbsent(parentSessionId, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count > subagentProperties.getMaxAutoContinuations()) {
            log.warn("已达自动继续上限 session={} count={}", parentSessionId, count);
            return;
        }

        String prompt = buildContinuationPrompt(subtask);
        CodingSessionContext.set(parentSessionId, task.getAgentId());
        try {
            taskAutoProvisioner.ensureActiveTask(parentSessionId, task.getAgentId());
            JChatMind orchestrator = jChatMindFactory.createContinuation(
                    task.getAgentId(), parentSessionId, prompt);
            log.info("子任务 {} 完成后自动继续编排 (#{})", subtask.getId(), count);
            orchestrator.run();
        } catch (Exception e) {
            log.error("自动继续编排失败 session={}: {}", parentSessionId, e.getMessage(), e);
        } finally {
            // 父 Orchestrator 会话仍在进行中，勿触发 Memory Hub 整理（避免 auto-continue 反复打 LLM/Ollama）
            CodingSessionContext.clear();
        }
    }

    private boolean hasRunningSubtasks(String parentSessionId) {
        List<CodingSubtaskDTO> subs = codingSubtaskService.listByParentSession(parentSessionId);
        return subs.stream().anyMatch(s ->
                CodingSubtaskStatus.RUNNING.getCode().equals(s.getStatus())
                        || CodingSubtaskStatus.PENDING.getCode().equals(s.getStatus()));
    }

    private boolean isOrchestratorAgent(String agentId) {
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

    private static String buildContinuationPrompt(CodingSubtaskDTO subtask) {
        String summary = subtask.getResultSummary() != null
                ? subtask.getResultSummary()
                : "(无摘要)";
        String status = subtask.getStatus();
        return """
                [系统自动继续] 子任务「%s」状态=%s。
                结果摘要：%s
                
                请继续编排直至用户原始需求全部完成：
                1. 用 list_coding_subtasks 查看进度
                2. 若仍有未完成部分，继续 delegate_coding_task 委派 Worker
                3. 全部完成后向用户汇总交付说明；最终调用 mark_coding_complete（仅 Orchestrator 可调用）
                不要停下来等待用户再发消息。
                """.formatted(subtask.getTitle(), status, summary);
    }
}
