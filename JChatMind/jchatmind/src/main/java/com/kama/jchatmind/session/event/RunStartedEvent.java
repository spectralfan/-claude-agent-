package com.kama.jchatmind.session.event;
public class RunStartedEvent extends Event {
    private String type = "run.started"; private String runId; private String goal; private String ts;
    protected RunStartedEvent() {}
    public RunStartedEvent(String runId, String goal, String ts) { this.runId = runId; this.goal = goal; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public String getGoal() { return goal; } public String getTs() { return ts; }
}