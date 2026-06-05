package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CodingTaskDTO {
    private String id;
    private String sessionId;
    private String agentId;
    private String status;
    private String workspacePath;
    private String workspaceRoot;
    private String command;
    private String resultSummary;
    private String approvalReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String skillId;
    private String stackId;
    private String language;
    private String approvalMode;
}
