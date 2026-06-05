package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CodingSubtaskDTO {
    private String id;
    private String parentSessionId;
    private String parentTaskId;
    private String title;
    private String goal;
    private String workerAgentId;
    private String status;
    private String resultSummary;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
