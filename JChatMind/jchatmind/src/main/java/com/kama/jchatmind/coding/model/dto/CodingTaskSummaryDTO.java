package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CodingTaskSummaryDTO {
    private String taskId;
    private String status;
    private String stackId;
    private String language;
    private String resultSummary;
    private String lastCommand;
    private String lastCommandOutput;
    private boolean completed;
    @Builder.Default
    private List<String> changedFiles = new ArrayList<>();
    private String runInstructions;
}
