package com.kama.jchatmind.coding.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodingAgentPresetDefinition {
    private String presetKey;
    private String name;
    private String description;
    private String systemPrompt;
    private String model;
    private List<String> allowedTools = new ArrayList<>();
    private List<String> allowedKbs = new ArrayList<>();
}
