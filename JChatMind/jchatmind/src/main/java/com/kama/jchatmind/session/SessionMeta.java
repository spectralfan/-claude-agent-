package com.kama.jchatmind.session;

import java.time.LocalDateTime;

/**
 * Session 元数据，持久化到 meta.json。
 */
public class SessionMeta {

    private String sessionId;
    private String agentId;
    private String title;
    private SessionState state;
    private int version;
    private int runCount;
    private String lastRunId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;

    public SessionMeta() {}

    public SessionMeta(String sessionId, String agentId, String title) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.title = title;
        this.state = SessionState.CREATED;
        this.version = 0;
        this.runCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public int getRunCount() { return runCount; }
    public void setRunCount(int runCount) { this.runCount = runCount; }
    public String getLastRunId() { return lastRunId; }
    public void setLastRunId(String lastRunId) { this.lastRunId = lastRunId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
}