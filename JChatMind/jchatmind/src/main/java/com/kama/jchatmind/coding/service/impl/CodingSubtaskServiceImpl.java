package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CodingSubtaskServiceImpl implements CodingSubtaskService {

    private final OrchestrationTaskService orchestrationTaskService;

    @Override
    public CodingSubtaskDTO create(String parentSessionId, String parentTaskId, String workerAgentId,
                                   String title, String goal) {
        OrchestrationTaskDTO created = orchestrationTaskService.create(
                parentSessionId,
                parentTaskId,
                OrchestrationTaskRole.WORKER,
                title,
                goal,
                null,
                null,
                List.of(),
                workerAgentId,
                1,
                null,
                null
        );
        return toSubtask(created);
    }

    @Override
    public Optional<CodingSubtaskDTO> findById(String subTaskId) {
        return orchestrationTaskService.findById(subTaskId).map(this::toSubtask);
    }

    @Override
    public List<CodingSubtaskDTO> listByParentSession(String parentSessionId) {
        return orchestrationTaskService.listByParentSession(parentSessionId).stream()
                .map(this::toSubtask)
                .toList();
    }

    @Override
    public void markRunning(String subTaskId) {
        orchestrationTaskService.markRunning(subTaskId);
    }

    @Override
    public void markCompleted(String subTaskId, String summary) {
        orchestrationTaskService.markCompleted(subTaskId, summary);
    }

    @Override
    public void markFailed(String subTaskId, String errorMessage) {
        orchestrationTaskService.markFailed(subTaskId, errorMessage);
    }

    private CodingSubtaskDTO toSubtask(OrchestrationTaskDTO dto) {
        if (dto == null) {
            return null;
        }
        return CodingSubtaskDTO.builder()
                .id(dto.getId())
                .parentSessionId(dto.getParentSessionId())
                .parentTaskId(dto.getParentTaskId())
                .role(dto.getRole())
                .title(dto.getTitle())
                .goal(dto.getGoal())
                .constraints(dto.getConstraints())
                .contextFiles(dto.getContextFiles())
                .dependsOn(dto.getDependsOn())
                .workerAgentId(dto.getWorkerAgentId())
                .status(dto.getStatus())
                .resultSummary(dto.getResultSummary())
                .errorMessage(dto.getErrorMessage())
                .depth(dto.getDepth())
                .spawnedFromTaskId(dto.getSpawnedFromTaskId())
                .createdAt(dto.getCreatedAt())
                .finishedAt(dto.getFinishedAt())
                .build();
    }
}
