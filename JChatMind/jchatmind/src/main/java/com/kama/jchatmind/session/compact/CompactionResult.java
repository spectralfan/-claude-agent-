package com.kama.jchatmind.session.compact;

public class CompactionResult {
    private final String summaryText;
    private final int originalTokenEstimate;
    private final int summaryTokens;

    public CompactionResult(String summaryText, int originalTokenEstimate, int summaryTokens) {
        this.summaryText = summaryText; this.originalTokenEstimate = originalTokenEstimate; this.summaryTokens = summaryTokens;
    }

    public String getSummaryText() { return summaryText; }
    public int getOriginalTokenEstimate() { return originalTokenEstimate; }
    public int getSummaryTokens() { return summaryTokens; }
}