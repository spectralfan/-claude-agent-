package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.agent.profile.AgentProfile;
import com.kama.jchatmind.agent.profile.AgentProfileService;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.session.event.EventBus;
import com.kama.jchatmind.session.event.SubagentStartedEvent;
import com.kama.jchatmind.session.event.SubagentFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SpawnAgentTool implements Tool {

    @Autowired
    private ApplicationContext applicationContext;

    private final AgentMapper agentMapper;
    private final AgentProfileService profileService;

    private static final ConcurrentHashMap<String, CompletableFuture<String>> backgroundTasks = new ConcurrentHashMap<>();

    public SpawnAgentTool(AgentMapper agentMapper, AgentProfileService profileService) {
        this.agentMapper = agentMapper;
        this.profileService = profileService;
    }

    private JChatMindFactory getFactory() {
        return applicationContext.getBean(JChatMindFactory.class);
    }

    private EventBus getEventBus() {
        return applicationContext.getBean(EventBus.class);
    }

    @Override
    public String getName() { return "spawn_agent"; }

    @Override
    public String getDescription() { return "创建子智能体执行独立子任务并返回完整结果"; }

    @Override
    public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "spawn_agent",
            description = "创建子智能体执行独立子任务并返回完整结果。"
                    + "子 Agent 以干净的上下文启动（仅含 prompt），不继承当前对话历史。"
                    + "子 Agent 完整运行结束后，其最终输出会作为本工具的返回值回到你的上下文中。"
                    + "参数：prompt - 子任务的目标说明（必填），description - 简短描述（可选），"
                    + "subagent_type - 子智能体类型: planner(规划)/executor(执行,默认)/reviewer(审查)，"
                    + "run_in_background - 是否后台并行执行（默认false），后台模式返回 run_id，"
                    + "之后用 agent_result(run_id) 获取结果"
    )
    public String spawnAgent(
            String prompt, String description, String subagent_type, Boolean run_in_background) {

        if (prompt == null || prompt.isBlank()) {
            return "错误：prompt 不能为空";
        }
        // 子 Agent 不能再 spawn 子 Agent（深度限制 1 层）
        if (SubAgentRunContext.get() != null) {
            return "错误：子 Agent 不能继续创建子 Agent（嵌套层级限制）";
        }

        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String parentSessionId = ctx != null ? ctx.sessionId() : null;
        String subSessionId = UUID.randomUUID().toString();

        String type = (subagent_type != null && !subagent_type.isBlank())
                ? subagent_type.trim().toLowerCase() : "executor";
        if ("executor".equals(type)) type = "worker";

        boolean bg = run_in_background != null && run_in_background;

        log.info("SpawnAgent: type={}, bg={}, prompt={}, subSessionId={}",
                type, bg, prompt.substring(0, Math.min(prompt.length(), 80)), subSessionId);

        String evtTs = java.time.Instant.now().toString();
        String desc = (description != null && !description.isBlank())
                ? description : prompt.substring(0, Math.min(prompt.length(), 60));
        getEventBus().publish(new SubagentStartedEvent(subSessionId, parentSessionId, desc, evtTs));

        try {
            JChatMind subAgent;
            AgentProfile profile = profileService.getProfile(type).orElse(null);
            if (profile != null) {
                subAgent = getFactory().createProfileSubAgent(profile, parentSessionId, subSessionId, prompt);
            } else {
                String agentId = resolveAgentId(type);
                if (agentId == null) {
                    getEventBus().publish(new SubagentFinishedEvent(subSessionId, parentSessionId, "failed", java.time.Instant.now().toString()));
                    return "错误：未找到子智能体类型或 Agent: " + type;
                }
                subAgent = getFactory().createSpawnSubAgent(agentId, parentSessionId, subSessionId, prompt);
            }

            if (bg) {
                JChatMind subAgentFinal = subAgent;
                CodingSessionContext.Context bgCtx = CodingSessionContext.get();
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    if (bgCtx != null) {
                        CodingSessionContext.set(bgCtx.sessionId(), bgCtx.agentId());
                    }
                    SubAgentRunContext.set(parentSessionId, subSessionId, desc);
                    try {
                        subAgentFinal.run();
                        return subAgentFinal.getFinalOutput();
                    } catch (Exception e) {
                        getEventBus().publish(new SubagentFinishedEvent(subSessionId, parentSessionId, "failed", java.time.Instant.now().toString()));
                        log.error("Background SpawnAgent failed: subSessionId={}", subSessionId, e);
                        throw new RuntimeException(e);
                    } finally {
                        SubAgentRunContext.clear();
                        CodingSessionContext.clear();
                    }
                });
                backgroundTasks.put(subSessionId, future);
                return "子智能体已后台启动，run_id=" + subSessionId
                        + "。使用 agent_result(run_id=\"" + subSessionId + "\") 获取结果。";
            }

            // Foreground mode: blocking
            CodingSessionContext.Context parentCtx = CodingSessionContext.get();
            CodingSessionContext.set(subSessionId, null);
            SubAgentRunContext.set(parentSessionId, subSessionId, desc);
            try {
                subAgent.run();
            } finally {
                SubAgentRunContext.clear();
                if (parentCtx != null) {
                    CodingSessionContext.set(parentCtx.sessionId(), parentCtx.agentId());
                } else {
                    CodingSessionContext.clear();
                }
            }
            String result = subAgent.getFinalOutput();
            getEventBus().publish(new SubagentFinishedEvent(subSessionId, parentSessionId, "success", java.time.Instant.now().toString()));
            log.info("SpawnAgent done: subSessionId={}, resultLength={}",
                    subSessionId, result != null ? result.length() : 0);
            return result != null && !result.isEmpty() ? result : "（子智能体执行完成，无文本输出）";

        } catch (Exception e) {
            getEventBus().publish(new SubagentFinishedEvent(subSessionId, parentSessionId, "failed", java.time.Instant.now().toString()));
            log.error("SpawnAgent failed: subSessionId={}", subSessionId, e);
            return "错误：子智能体执行失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "agent_result",
            description = "查询后台子智能体的执行结果。参数：run_id - spawn_agent 返回的 run_id"
    )
    public String agentResult(String run_id) {
        if (run_id == null || run_id.isBlank()) {
            return "错误：run_id 不能为空";
        }
        CompletableFuture<String> future = backgroundTasks.get(run_id);
        if (future == null) {
            return "错误：未找到该 run_id，可能已过期或无效";
        }
        if (!future.isDone()) {
            return "子智能体仍在运行中，请稍后重试";
        }
        try {
            String result = future.get();
            backgroundTasks.remove(run_id);
            return result;
        } catch (Exception e) {
            backgroundTasks.remove(run_id);
            return "错误：子智能体执行失败 - " + e.getMessage();
        }
    }

    private String resolveAgentId(String agentInput) {
        if (agentInput == null || agentInput.isBlank()) {
            Agent defaultAgent = agentMapper.selectByName("Claude Code Coding Agent");
            if (defaultAgent != null) return defaultAgent.getId();
            var agents = agentMapper.selectAll();
            return (agents != null && !agents.isEmpty()) ? agents.get(0).getId() : null;
        }
        Agent byName = agentMapper.selectByName(agentInput);
        if (byName != null) return byName.getId();
        return agentInput;
    }
}