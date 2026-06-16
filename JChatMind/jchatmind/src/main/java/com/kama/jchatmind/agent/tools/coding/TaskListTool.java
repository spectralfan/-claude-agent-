package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.task.TaskManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 列出所有任务及状态（对齐 KamaClaude TaskListTool）。
 * worker 可用。
 */
@Slf4j
@Component
public class TaskListTool implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;

    public TaskListTool(CodingTaskService codingTaskService,
                        CodingWorkspaceService workspaceService) {
        this.codingTaskService = codingTaskService;
        this.workspaceService = workspaceService;
    }

    @Override public String getName() { return "task_list"; }
    @Override public String getDescription() {
        return "List all tasks with their current status and blocking dependencies. Use this to check what work remains and what can be started next.";
    }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "task_list",
            description = "List all tasks with their current status and blocking dependencies. "
                    + "Use this to check what work remains and what can be started next."
    )
    public String listTasks() {
        try {
            TaskManager tm = getTaskManager();
            return tm.formatList();
        } catch (Exception e) {
            return "task_list 失败: " + e.getMessage();
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