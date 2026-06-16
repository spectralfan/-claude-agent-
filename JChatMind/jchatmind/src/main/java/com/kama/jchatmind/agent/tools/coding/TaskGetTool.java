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

/**
 * 读取单个任务详情（对齐 KamaClaude TaskGetTool）。
 * 根 Agent 和 reviewer 可用（只读）。
 */
@Slf4j
@Component
public class TaskGetTool implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;

    public TaskGetTool(CodingTaskService codingTaskService,
                       CodingWorkspaceService workspaceService) {
        this.codingTaskService = codingTaskService;
        this.workspaceService = workspaceService;
    }

    @Override public String getName() { return "task_get"; }
    @Override public String getDescription() {
        return "Get full details (JSON) of a specific task by ID.";
    }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "task_get",
            description = "Get full details of a specific task by ID."
    )
    public String getTask(int task_id) {
        try {
            TaskManager tm = getTaskManager();
            AgentTask task = tm.get(task_id);
            return String.format(
                    "{\"id\":%d,\"subject\":\"%s\",\"description\":\"%s\",\"status\":\"%s\","
                            + "\"blocked_by\":%s,\"created_at\":\"%s\",\"updated_at\":\"%s\"}",
                    task.getId(),
                    escape(task.getSubject()),
                    escape(task.getDescription()),
                    task.getStatus(),
                    task.getBlockedBy(),
                    task.getCreatedAt(),
                    task.getUpdatedAt()
            );
        } catch (Exception e) {
            return "task_get 失败: " + e.getMessage();
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

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}