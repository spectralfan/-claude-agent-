package com.kama.jchatmind.session.event;
public class PermissionGrantedEvent extends Event {
    private String type = "permission.granted"; private String runId; private String toolUseId;
    private String decision; private String ts;
    protected PermissionGrantedEvent() {}
    public PermissionGrantedEvent(String runId, String toolUseId, String decision, String ts) {
        this.runId = runId; this.toolUseId = toolUseId; this.decision = decision; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getToolUseId() { return toolUseId; } public String getDecision() { return decision; }
    public String getTs() { return ts; }
}