package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.enums.CodingSubtaskStatus;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodingSubtaskServiceImpl implements CodingSubtaskService {

    private final Map<String, CodingSubtaskDTO> store = new ConcurrentHashMap<>();

    @Override
    public CodingSubtaskDTO create(String parentSessionId, String parentTaskId, String workerAgentId,
                                   String title, String goal) {
        String id = UUID.randomUUID().toString();
        CodingSubtaskDTO dto = CodingSubtaskDTO.builder()
                .id(id)
                .parentSessionId(parentSessionId)
                .parentTaskId(parentTaskId)
                .title(title != null && !title.isBlank() ? title.trim() : "子任务")
                .goal(goal)
                .workerAgentId(workerAgentId)
                .status(CodingSubtaskStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .build();
        store.put(id, dto);
        return dto;
    }

    @Override
    public Optional<CodingSubtaskDTO> findById(String subTaskId) {
        return Optional.ofNullable(store.get(subTaskId));
    }

    @Override
    public List<CodingSubtaskDTO> listByParentSession(String parentSessionId) {
        List<CodingSubtaskDTO> list = new ArrayList<>();
        for (CodingSubtaskDTO dto : store.values()) {
            if (parentSessionId.equals(dto.getParentSessionId())) {
                list.add(dto);
            }
        }
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    @Override
    public void markRunning(String subTaskId) {
        updateStatus(subTaskId, CodingSubtaskStatus.RUNNING, null, null);
    }

    @Override
    public void markCompleted(String subTaskId, String summary) {
        updateStatus(subTaskId, CodingSubtaskStatus.COMPLETED, summary, null);
    }

    @Override
    public void markFailed(String subTaskId, String errorMessage) {
        updateStatus(subTaskId, CodingSubtaskStatus.FAILED, null, errorMessage);
    }

    private void updateStatus(String subTaskId, CodingSubtaskStatus status,
                              String summary, String error) {
        CodingSubtaskDTO existing = store.get(subTaskId);
        if (existing == null) {
            return;
        }
        CodingSubtaskDTO updated = CodingSubtaskDTO.builder()
                .id(existing.getId())
                .parentSessionId(existing.getParentSessionId())
                .parentTaskId(existing.getParentTaskId())
                .title(existing.getTitle())
                .goal(existing.getGoal())
                .workerAgentId(existing.getWorkerAgentId())
                .status(status.getCode())
                .resultSummary(summary != null ? summary : existing.getResultSummary())
                .errorMessage(error != null ? error : existing.getErrorMessage())
                .createdAt(existing.getCreatedAt())
                .finishedAt(status == CodingSubtaskStatus.RUNNING ? null : LocalDateTime.now())
                .build();
        store.put(subTaskId, updated);
    }
}
