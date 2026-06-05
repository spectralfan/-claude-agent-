package com.kama.jchatmind.coding.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.RunMavenRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingTaskStatus;
import com.kama.jchatmind.coding.service.CodingApprovalService;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodingApprovalServiceImpl implements CodingApprovalService {

    private final CodingTaskService codingTaskService;
    private final CodingCommandService codingCommandService;
    private final ObjectMapper objectMapper;

    @Override
    public CommandExecutionResult approve(String taskId) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        if (!CodingTaskStatus.WAITING_APPROVAL.getCode().equals(task.getStatus())) {
            throw new IllegalStateException("任务不在待审批状态: " + taskId);
        }
        try {
            RunMavenRequest request = objectMapper.readValue(task.getPendingPayload(), RunMavenRequest.class);
            codingTaskService.clearPending(taskId);
            return codingCommandService.executeMaven(request);
        } catch (Exception e) {
            throw new IllegalStateException("解析审批载荷失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void reject(String taskId, String reason) {
        codingTaskService.rejectTask(taskId, reason == null ? "用户拒绝审批" : reason);
    }
}
