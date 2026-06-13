package com.kama.jchatmind.session.event;
public class RunFinishedEvent extends Event {
    private String type = "run.finished"; private String runId; private String status;
    private String reason; private int steps; private String ts;
    protected RunFinishedEvent() {}
    public RunFinishedEvent(String runId, String status, String reason, int steps, String ts) {
        this.runId = runId; this.status = status; this.reason = reason; this.steps = steps; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getStatus() { return status; } public String getReason() { return reason; }
    public int getSteps() { return steps; } public String getTs() { return ts; }
}