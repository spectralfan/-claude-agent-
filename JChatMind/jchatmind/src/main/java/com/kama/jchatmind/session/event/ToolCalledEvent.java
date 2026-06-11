package com.kama.jchatmind.session.event;

public class ToolCalledEvent {
    private final String runId;
    private final String toolName;
    private final String arguments;
    private final int step;

    public ToolCalledEvent(String runId, String toolName, String arguments, int step) {
        this.runId = runId; this.toolName = toolName; this.arguments = arguments; this.step = step;
    }

    public String getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public String getArguments() { return arguments; }
    public int getStep() { return step; }
}