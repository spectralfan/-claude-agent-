package com.kama.jchatmind.session.event;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolResultEvent extends Event {
    private String type = "tool.result"; private String runId; private String toolName;
    private String result; private boolean errorField; private int step; private String ts;
        protected ToolResultEvent() {}
    public ToolResultEvent(String runId, String toolName, String result, boolean isError, int step) {
        this.runId = runId; this.toolName = toolName; this.result = result; this.errorField = isError; this.step = step; this.ts = java.time.Instant.now().toString(); }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getToolName() { return toolName; } public String getResult() { return result; }
    public boolean isError() { return errorField; } public int getStep() { return step; }
    public String getTs() { return ts; }
}