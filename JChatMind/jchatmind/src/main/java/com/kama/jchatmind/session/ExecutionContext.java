package com.kama.jchatmind.session;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.ArrayList;
import java.util.List;

public class ExecutionContext {
    private final String runId; private final String goal; private final int maxSteps;
    private final List<Message> messages;
    private int step; private String status; private String reason; private String result;
    private String sessionNotes; private String systemPromptOverride;
    private double contextPct;

    public ExecutionContext(String runId, String goal, int maxSteps) {
        this.runId = runId; this.goal = goal; this.maxSteps = maxSteps;
        this.messages = new ArrayList<>(); this.step = 0; this.status = "running";
        this.messages.add(new UserMessage(goal));
    }

    public String getRunId() { return runId; }
    public String getGoal() { return goal; }
    public int getMaxSteps() { return maxSteps; }
    public List<Message> getMessages() { return messages; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public void incrementStep() { this.step++; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getSessionNotes() { return sessionNotes; }
    public void setSessionNotes(String notes) { this.sessionNotes = notes; }
    public String getSystemPromptOverride() { return systemPromptOverride; }
    public void setSystemPromptOverride(String sp) { this.systemPromptOverride = sp; }
    public double getContextPct() { return contextPct; }
    public void setContextPct(double pct) { this.contextPct = pct; }
    public boolean isDone() { return !"running".equals(status); }
    public void markSuccess() { this.status = "success"; }
    public void markFailed(String reason) { this.status = "failed"; this.reason = reason; }
}