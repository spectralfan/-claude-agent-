package com.kama.jchatmind.session.event;
public class ContextCompactedEvent extends Event {
    private String type = "context.compacted"; private String sessionId; private String runId;
    private int originalTokens; private int summaryTokens; private String ts;
    protected ContextCompactedEvent() {}
    public ContextCompactedEvent(String sessionId, String runId, int originalTokens, int summaryTokens, String ts) {
        this.sessionId = sessionId; this.runId = runId; this.originalTokens = originalTokens; this.summaryTokens = summaryTokens; this.ts = ts; }
    public String getType() { return type; } public String getSessionId() { return sessionId; }
    public String getRunId() { return runId; } public int getOriginalTokens() { return originalTokens; }
    public int getSummaryTokens() { return summaryTokens; } public String getTs() { return ts; }
}