package com.kama.jchatmind.session.event;
public class LlmUsageEvent extends Event {
    private String type = "llm.usage"; private String runId;
    private int inputTokens; private int outputTokens; private double contextPct; private String ts;
    protected LlmUsageEvent() {}
    public LlmUsageEvent(String runId, int inputTokens, int outputTokens, double contextPct, String ts) {
        this.runId = runId; this.inputTokens = inputTokens; this.outputTokens = outputTokens; this.contextPct = contextPct; this.ts = ts; }
    public String getType() { return type; } public String getRunId() { return runId; }
    public int getInputTokens() { return inputTokens; } public int getOutputTokens() { return outputTokens; }
    public double getContextPct() { return contextPct; } public String getTs() { return ts; }
}