package com.kama.jchatmind.coding.task;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 自主规划用的轻量任务模型（对齐 KamaClaude Task）。
 * 3 状态：pending → in_progress → completed，支持 blocked_by 依赖。
 */
public class AgentTask {

    private final int id;
    private final String subject;
    private final String description;
    private String status;
    private final List<Integer> blockedBy;
    private final String createdAt;
    private String updatedAt;

    public AgentTask(int id, String subject, String description, String status,
                     List<Integer> blockedBy, String createdAt, String updatedAt) {
        this.id = id;
        this.subject = subject;
        this.description = description;
        this.status = status;
        this.blockedBy = blockedBy != null ? new ArrayList<>(blockedBy) : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() { return id; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Integer> getBlockedBy() { return blockedBy; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** 格式化单行摘要，供 task_list 展示 */
    public String formatLine() {
        String marker = switch (status) {
            case "completed" -> "[x]";
            case "in_progress" -> "[>]";
            default -> "[ ]";
        };
        String blocked = blockedBy.isEmpty() ? "" : " (blocked by: " + blockedBy + ")";
        return marker + " #" + id + ": " + subject + blocked;
    }
}