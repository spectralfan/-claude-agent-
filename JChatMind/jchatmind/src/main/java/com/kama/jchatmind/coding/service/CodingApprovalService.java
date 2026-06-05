package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;

public interface CodingApprovalService {

    CommandExecutionResult approve(String taskId);

    void reject(String taskId, String reason);
}
