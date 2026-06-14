package com.kama.jchatmind.session.event;
public class SubagentFinishedEvent extends Event {
    private String type = "subagent.finished"; private String runId; private String parentRunId;
    private String status; private String ts;
    protected SubagentFinishedEvent() {}
    public SubagentFinishedEvent(String runId, String parentRunId, String status, String ts) {
        this.runId = runId; this.parentRunId = parentRunId; this.status = status; this.ts = ts;
    }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getParentRunId() { return parentRunId; }
    public String getStatus() { return status; } public String getTs() { return ts; }
}
