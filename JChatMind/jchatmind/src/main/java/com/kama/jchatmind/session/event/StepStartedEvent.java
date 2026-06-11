package com.kama.jchatmind.session.event;

public class StepStartedEvent {
    private final String runId;
    private final int step;
    private final String ts;

    public StepStartedEvent(String runId, int step, String ts) {
        this.runId = runId;
        this.step = step;
        this.ts = ts;
    }

    public String getRunId() { return runId; }
    public int getStep() { return step; }
    public String getTs() { return ts; }
}