package com.kama.jchatmind.coding.orchestration;

import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;

/**
 * 编排任务进入终态（COMPLETED/FAILED），触发自动 Reviewer 与后续调度。
 */
public record OrchestrationTaskFinishedEvent(OrchestrationTaskDTO task) {
}
