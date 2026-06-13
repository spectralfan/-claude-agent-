package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class SpawnAgentTool implements Tool {

    @Autowired
    private ApplicationContext applicationContext;

    private final AgentMapper agentMapper;

    public SpawnAgentTool(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    private JChatMindFactory getFactory() {
        return applicationContext.getBean(JChatMindFactory.class);
    }

    @Override
    public String getName() {
        return "spawn_agent";
    }

    @Override
    public String getDescription() {
        return "创建子智能体执行独立子任务并返回完整结果。子 Agent 不继承当前对话历史。";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "spawn_agent",
            description = "创建子智能体执行独立子任务并返回完整结果。"
                    + "子 Agent 以干净的上下文启动（仅含 goal），不继承当前对话历史。"
                    + "子 Agent 完整运行结束后，其最终输出会作为本工具的返回值回到你的上下文中。"
                    + "参数：goal - 子任务的目标说明（必填），agentId - 子智能体 ID（可选）"
    )
    public String spawnAgent(String goal, String agentId) {
        if (goal == null || goal.isBlank()) {
            return "错误：goal 不能为空";
        }
        if (SubAgentRunContext.get() != null) {
            return "错误：子 Agent 不能继续创建子 Agent（嵌套层级限制）";
        }

        CodingSessionContext.Context ctx = CodingSessionContext.get();
        String parentSessionId = ctx != null ? ctx.sessionId() : null;

        String subAgentId = agentId;
        if (subAgentId == null || subAgentId.isBlank()) {
            Agent defaultAgent = agentMapper.selectByName("Claude Code Coding Agent");
            if (defaultAgent == null) {
                var agents = agentMapper.selectAll();
                if (agents != null && !agents.isEmpty()) {
                    subAgentId = agents.get(0).getId();
                } else {
                    return "错误：未找到可用子智能体";
                }
            } else {
                subAgentId = defaultAgent.getId();
            }
        }

        String subSessionId = UUID.randomUUID().toString();
        log.info("SpawnAgent: goal={}, agentId={}, subSessionId={}",
                goal.substring(0, Math.min(goal.length(), 80)), subAgentId, subSessionId);

        try {
            JChatMind subAgent = getFactory().createSpawnSubAgent(
                    subAgentId, parentSessionId, subSessionId, goal);
            subAgent.run();

            String result = subAgent.getFinalOutput();
            log.info("SpawnAgent 完成: subSessionId={}, resultLength={}",
                    subSessionId, result != null ? result.length() : 0);
            return result != null && !result.isEmpty() ? result : "（子智能体执行完成，无文本输出）";
        } catch (Exception e) {
            log.error("SpawnAgent 执行失败: subSessionId={}", subSessionId, e);
            return "错误：子智能体执行失败 - " + e.getMessage();
        }
    }
}