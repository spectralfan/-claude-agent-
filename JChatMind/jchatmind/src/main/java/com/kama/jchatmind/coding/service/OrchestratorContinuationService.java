package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;

/**
 * 子任务完成后自动唤醒 Orchestrator，直至整单交付（无需用户反复发消息）。
 */
public interface OrchestratorContinuationService {

    void onSubtaskFinished(CodingSubtaskDTO subtask);
}
