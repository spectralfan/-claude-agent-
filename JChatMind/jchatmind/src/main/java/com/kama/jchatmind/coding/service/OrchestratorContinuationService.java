package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;

/**
 * 子任务/编排任务完成后自动唤醒 Scheduler，直至整单交付。
 */
public interface OrchestratorContinuationService {

    void onSubtaskFinished(CodingSubtaskDTO subtask);

    void onOrchestrationTaskFinished(OrchestrationTaskDTO task);
}
