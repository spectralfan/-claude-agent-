package com.kama.jchatmind.coding.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.OrchestrationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrchestrationTaskConverter {

    private final ObjectMapper objectMapper;

    public OrchestrationTaskDTO toDto(OrchestrationTask entity) {
        if (entity == null) {
            return null;
        }
        return OrchestrationTaskDTO.builder()
                .id(entity.getId())
                .parentSessionId(entity.getParentSessionId())
                .parentTaskId(entity.getParentCodingTaskId())
                .role(entity.getRole())
                .title(entity.getTitle())
                .goal(entity.getGoal())
                .constraints(entity.getConstraints())
                .contextFiles(parseStringList(entity.getContextFiles()))
                .dependsOn(parseStringList(entity.getDependsOn()))
                .status(entity.getStatus())
                .depth(entity.getDepth())
                .spawnedFromTaskId(entity.getSpawnedFromTaskId())
                .workerAgentId(entity.getWorkerAgentId())
                .resultSummary(entity.getResultSummary())
                .errorMessage(entity.getErrorMessage())
                .metadata(parseMetadata(entity.getMetadata()))
                .createdAt(entity.getCreatedAt())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }

    public String toJsonList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : Collections.emptyList());
        } catch (Exception e) {
            return "[]";
        }
    }

    public String toJsonMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Collections.emptyMap());
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
