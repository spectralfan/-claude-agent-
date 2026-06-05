package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;

import java.util.List;
import java.util.Optional;

public interface CodingSubtaskService {

    CodingSubtaskDTO create(String parentSessionId, String parentTaskId, String workerAgentId,
                            String title, String goal);

    Optional<CodingSubtaskDTO> findById(String subTaskId);

    List<CodingSubtaskDTO> listByParentSession(String parentSessionId);

    void markRunning(String subTaskId);

    void markCompleted(String subTaskId, String summary);

    void markFailed(String subTaskId, String errorMessage);
}
