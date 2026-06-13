package com.kama.jchatmind.session.event;
public class StepStartedEvent extends Event {
    private String type = "step.started"; private String runId; private int step; private String ts;
    protected StepStartedEvent() {}
    public StepStartedEvent(String runId, int step, String ts) { this.runId = runId; this.step = step; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public int getStep() { return step; } public String getTs() { return ts; }
}