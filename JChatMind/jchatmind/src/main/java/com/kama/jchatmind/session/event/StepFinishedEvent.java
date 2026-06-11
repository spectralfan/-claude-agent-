package com.kama.jchatmind.session.event;

public class StepFinishedEvent {
    private final String runId;
    private final int step;
    private final String ts;
    private final String status;

    public StepFinishedEvent(String runId, int step, String ts, String status) {
        this.runId = runId; this.step = step; this.ts = ts; this.status = status;
    }

    public String getRunId() { return runId; }
    public int getStep() { return step; }
    public String getTs() { return ts; }
    public String getStatus() { return status; }
}