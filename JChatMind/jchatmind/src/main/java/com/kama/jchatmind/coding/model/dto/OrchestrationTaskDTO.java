package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class OrchestrationTaskDTO {

    private String id;
    private String parentSessionId;
    private String parentTaskId;
    private String role;
    private String title;
    private String goal;
    private String constraints;
    private List<String> contextFiles;
    private List<String> dependsOn;
    private String status;
    private int depth;
    private String spawnedFromTaskId;
    private String workerAgentId;
    private String resultSummary;
    private String errorMessage;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
