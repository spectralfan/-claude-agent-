package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodingAgentPresetDTO {
    private String presetKey;
    private String agentId;
    private String name;
    private String description;
    private String model;
    private List<String> allowedTools;
}
