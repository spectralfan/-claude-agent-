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
 * 更新任务状态或依赖（对齐 KamaClaude TaskUpdateTool）。
 * planner 和 worker 可用。
 */
@Slf4j
@Component
public class TaskUpdateTool implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;

    public TaskUpdateTool(CodingTaskService codingTaskService,
                          CodingWorkspaceService workspaceService) {
        this.codingTaskService = codingTaskService;
        this.workspaceService = workspaceService;
    }

    @Override public String getName() { return "task_update"; }
    @Override public String getDescription() {
        return "Update a task status. Set to in_progress when starting, completed when finished (auto-clears from other tasks blocked_by).";
    }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "task_update",
            description = "Update a task status or dependency list. "
                    + "Set status to in_progress when starting work on a task, "
                    + "completed when finished (automatically clears it from other tasks blocked_by)."
    )
    public String updateTask(int task_id, String status,
                              List<Integer> add_blocked_by,
                              List<Integer> remove_blocked_by) {
        try {
            TaskManager tm = getTaskManager();
            AgentTask task = tm.update(task_id, status, add_blocked_by, remove_blocked_by);
            return String.format("任务已更新 #%d: %s (status=%s)",
                    task.getId(), task.getSubject(), task.getStatus());
        } catch (Exception e) {
            return "task_update 失败: " + e.getMessage();
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