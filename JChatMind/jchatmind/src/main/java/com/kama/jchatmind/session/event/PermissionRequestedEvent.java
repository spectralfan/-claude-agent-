package com.kama.jchatmind.session.event;
public class PermissionRequestedEvent extends Event {
    private String type = "permission.requested"; private String runId; private String toolName;
    private String paramPreview; private String sessionId; private String ts;
    protected PermissionRequestedEvent() {}
    public PermissionRequestedEvent(String runId, String toolName, String paramPreview, String sessionId, String ts) {
        this.runId = runId; this.toolName = toolName; this.paramPreview = paramPreview; this.sessionId = sessionId; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getToolName() { return toolName; } public String getParamPreview() { return paramPreview; }
    public String getSessionId() { return sessionId; } public String getTs() { return ts; }
}