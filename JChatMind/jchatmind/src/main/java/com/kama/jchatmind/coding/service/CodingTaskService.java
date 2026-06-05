package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CreateCodingTaskRequest;
import com.kama.jchatmind.coding.model.dto.CodingTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;

public interface CodingTaskService {

    String createTask(CreateCodingTaskRequest request);

    String createTask(String sessionId, String agentId, String workspaceRoot, String workspacePath);

    CodingTaskDTO getTask(String taskId);

    CodingTask getTaskEntity(String taskId);

    CodingTask getActiveTask(String sessionId);

    void markRunning(String taskId);

    void markWaitingApproval(String taskId, String command, String pendingAction, String pendingPayload);

    void completeTask(String taskId, String summary);

    void failTask(String taskId, String reason);

    void timeoutTask(String taskId);

    void rejectTask(String taskId, String reason);

    void clearPending(String taskId);

    /** Maven 等命令执行后记录结果，保持任务 RUNNING 以便 Agent 继续修复 */
    void recordExecutionResult(String taskId, String command, String resultSummary);
}
