package com.kama.jchatmind.coding.model.dto;

import com.kama.jchatmind.coding.model.dto.StackVerifyCommandDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CodingStackDTO {
    private String id;
    private String language;
    private String displayName;
    private List<String> detectFiles = new ArrayList<>();
    private String scaffoldTemplate;
    private String skillId;
    private List<String> suggestedMcpTools = new ArrayList<>();
    private String verifyWorkflow;
    private String doneCriteria;
    private List<String> suggestedAgentTools = new ArrayList<>();
    private List<StackVerifyCommandDTO> verifyCommands = new ArrayList<>();
}
