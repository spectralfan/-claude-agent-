package com.kama.jchatmind.agent.profile;

import java.util.List;

public class AgentProfile {
    private String name;
    private String description;
    private String systemPrompt;
    private List<String> allowedTools;
    private int maxSteps = 35;
    private String model;

    public AgentProfile() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public List<String> getAllowedTools() { return allowedTools; }
    public void setAllowedTools(List<String> allowedTools) { this.allowedTools = allowedTools; }
    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}