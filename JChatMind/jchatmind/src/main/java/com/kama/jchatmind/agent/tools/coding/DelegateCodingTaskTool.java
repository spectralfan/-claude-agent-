package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.entity.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelegateCodingTaskTool implements Tool {

    private final CodingSubagentProperties subagentProperties;
    private final OrchestrationTaskService orchestrationTaskService;
    private final CodingTaskService codingTaskService;
    private final CodingAgentPresetService codingAgentPresetService;
    private final AgentMapper agentMapper;

    @Override
    public String getName() {
        return "delegate_coding_task";
    }

    @Override
    public String getDescription() {
        return "（兼容）将 Coding 子目标异步委派给 Worker，等价 create_orchestration_task(role=WORKER)";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "delegate_coding_task",
            description = "将具体开发子任务委派给后台 Worker Agent 异步执行（兼容层）。"
                    + "goal 须为可独立完成的子目标（含验收标准）。"
                    + "委派后系统后台执行，勿 list 轮询；等待 [系统自动继续]。"
    )
    public String delegateCodingTask(String goal, String title) {
        if (!subagentProperties.isEnabled()) {
            return "错误：子 Agent 委派功能未启用";
        }
        if (SubAgentRunContext.get() != null) {
            return "错误：子 Agent 不能继续委派子任务";
        }
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return "错误：无 Coding 会话上下文";
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return "错误：当前会话无活动 Coding 任务";
        }
        if (goal == null || goal.isBlank()) {
            return "错误：goal 不能为空";
        }
        String workerAgentId = resolveWorkerAgentId();
        if (workerAgentId == null) {
            return "错误：未找到 Worker Agent（" + subagentProperties.getWorkerAgentName() + "）";
        }

        OrchestrationTaskDTO created = orchestrationTaskService.create(
                ctx.sessionId(),
                task.getId(),
                OrchestrationTaskRole.WORKER,
                title != null && !title.isBlank() ? title : "Worker 任务",
                goal.trim(),
                null,
                List.of(),
                List.of(),
                workerAgentId,
                1,
                null,
                null
        );
        return "子任务已创建（由 Dispatcher 异步执行）。"
                + " taskId=" + created.getId()
                + " status=" + created.getStatus()
                + " title=" + created.getTitle()
                + "。系统后台执行，勿 list 轮询；等待 [系统自动继续]。";
    }

    private String resolveWorkerAgentId() {
        return codingAgentPresetService.findPreset()
                .map(CodingAgentPresetDTO::getAgentId)
                .orElseGet(() -> {
                    Agent agent = findAgentByName(subagentProperties.getWorkerAgentName());
                    return agent != null ? agent.getId() : null;
                });
    }

    private Agent findAgentByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return agentMapper.selectByName(name);
    }
}
