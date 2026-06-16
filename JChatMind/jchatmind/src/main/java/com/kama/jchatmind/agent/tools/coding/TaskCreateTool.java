package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.task.AgentTask;
import com.kama.jchatmind.coding.task.TaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 创建新任务（对齐 KamaClaude TaskCreateTool）。
 * planner 和根 Agent 可用。
 */
@Slf4j
@Component
public class TaskCreateTool implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;

    public TaskCreateTool(CodingTaskService codingTaskService,
                          CodingWorkspaceService workspaceService) {
        this.codingTaskService = codingTaskService;
        this.workspaceService = workspaceService;
    }

    @Override public String getName() { return "task_create"; }
    @Override public String getDescription() {
        return "Create a new task to track a unit of work. Use this to break down a complex goal into smaller, trackable steps.";
    }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "task_create",
            description = "Create a new task to track a unit of work. "
                    + "Use this to break down a complex goal into smaller, trackable steps."
    )
    public String createTask(String subject, String description,
                              List<Integer> blocked_by) {
        try {
            TaskManager tm = getTaskManager();
            AgentTask task = tm.create(
                    subject != null ? subject : "",
                    description != null ? description : "",
                    blocked_by
            );
            return String.format("任务已创建 #%d: %s (status=%s)",
                    task.getId(), task.getSubject(), task.getStatus());
        } catch (Exception e) {
            return "task_create 失败: " + e.getMessage();
        }
    }

    private TaskManager getTaskManager() {
        CodingTask task = getActiveCodingTask();
        Path workspace = workspaceService.resolveForTask(task);
        return new TaskManager(workspace.resolve(".tasks"));
    }

    private CodingTask getActiveCodingTask() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) throw new IllegalStateException("无 Coding 会话上下文");
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) throw new IllegalStateException("当前会话无活动 Coding 任务");
        return task;
    }
}