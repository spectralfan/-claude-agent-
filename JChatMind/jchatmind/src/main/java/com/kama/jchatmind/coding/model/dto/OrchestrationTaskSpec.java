package com.kama.jchatmind.coding.model.dto;

import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrchestrationTaskSpec {

    private String taskId;
    private String parentSessionId;
    private String parentCodingTaskId;
    private OrchestrationTaskRole role;
    private String title;
    private String goal;
    private String constraints;
    private List<String> contextFiles;
    private String agentId;
    private int depth;
}
