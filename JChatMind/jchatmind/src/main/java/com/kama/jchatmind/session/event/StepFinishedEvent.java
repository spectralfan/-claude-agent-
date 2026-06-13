package com.kama.jchatmind.session.event;
public class StepFinishedEvent extends Event {
    private String type = "step.finished"; private String runId; private int step; private String ts; private String status;
    protected StepFinishedEvent() {}
    public StepFinishedEvent(String runId, int step, String ts, String status) {
        this.runId = runId; this.step = step; this.ts = ts; this.status = status; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public int getStep() { return step; } public String getTs() { return ts; }
    public String getStatus() { return status; }
}