package com.kama.jchatmind.session.event;
public class ToolCalledEvent extends Event {
    private String type = "tool.called"; private String runId; private String toolName;
    private String arguments; private int step; private String ts;
    protected ToolCalledEvent() {}
    public ToolCalledEvent(String runId, String toolName, String arguments, int step) {
        this.runId = runId; this.toolName = toolName; this.arguments = arguments; this.step = step; this.ts = java.time.Instant.now().toString(); }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getToolName() { return toolName; } public String getArguments() { return arguments; }
    public int getStep() { return step; } public String getTs() { return ts; }
}