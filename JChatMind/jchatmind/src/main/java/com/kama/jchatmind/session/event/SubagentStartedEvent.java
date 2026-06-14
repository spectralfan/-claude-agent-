package com.kama.jchatmind.session.event;
public class SubagentStartedEvent extends Event {
    private String type = "subagent.started"; private String runId; private String parentRunId;
    private String description; private String ts;
    protected SubagentStartedEvent() {}
    public SubagentStartedEvent(String runId, String parentRunId, String description, String ts) {
        this.runId = runId; this.parentRunId = parentRunId; this.description = description; this.ts = ts;
    }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getParentRunId() { return parentRunId; }
    public String getDescription() { return description; } public String getTs() { return ts; }
}
