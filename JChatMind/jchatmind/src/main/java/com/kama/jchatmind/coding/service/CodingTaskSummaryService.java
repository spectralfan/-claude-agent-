package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingTaskSummaryDTO;

public interface CodingTaskSummaryService {

    CodingTaskSummaryDTO getSummary(String taskId);
}
