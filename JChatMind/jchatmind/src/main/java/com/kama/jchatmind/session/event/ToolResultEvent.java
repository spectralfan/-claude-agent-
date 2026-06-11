package com.kama.jchatmind.session.event;

public class ToolResultEvent {
    private final String runId;
    private final String toolName;
    private final String result;
    private final boolean isError;
    private final int step;

    public ToolResultEvent(String runId, String toolName, String result, boolean isError, int step) {
        this.runId = runId; this.toolName = toolName; this.result = result; this.isError = isError; this.step = step;
    }

    public String getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public String getResult() { return result; }
    public boolean isError() { return isError; }
    public int getStep() { return step; }
}