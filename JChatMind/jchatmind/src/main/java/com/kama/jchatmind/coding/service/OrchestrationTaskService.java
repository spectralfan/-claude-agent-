package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface OrchestrationTaskService {

    OrchestrationTaskDTO create(
            String parentSessionId,
            String parentCodingTaskId,
            OrchestrationTaskRole role,
            String title,
            String goal,
            String constraints,
            List<String> contextFiles,
            List<String> dependsOn,
            String workerAgentId,
            int depth,
            String spawnedFromTaskId,
            Map<String, Object> metadata);

    Optional<OrchestrationTaskDTO> findById(String taskId);

    List<OrchestrationTaskDTO> listByParentSession(String parentSessionId);

    List<OrchestrationTaskDTO> listReadyByParentSession(String parentSessionId);

    int countRunning(String parentSessionId);

    OrchestrationTaskDTO update(
            String taskId,
            String status,
            String goal,
            String constraints,
            List<String> dependsOn);

    void markRunning(String taskId);

    void markCompleted(String taskId, String summary);

    void markFailed(String taskId, String errorMessage);

    void refreshDependents(String parentSessionId);

    boolean allTerminal(String parentSessionId);

    boolean hasRunning(String parentSessionId);
}
