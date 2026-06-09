package com.kama.jchatmind.coding.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrchestrationTask {

    private String id;
    private String parentSessionId;
    private String parentCodingTaskId;
    private String role;
    private String title;
    private String goal;
    private String constraints;
    /** JSON array of relative file paths */
    private String contextFiles;
    /** JSON array of task UUIDs */
    private String dependsOn;
    private String status;
    private int depth;
    private String spawnedFromTaskId;
    private String workerAgentId;
    private String resultSummary;
    private String errorMessage;
    /** JSON object */
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
