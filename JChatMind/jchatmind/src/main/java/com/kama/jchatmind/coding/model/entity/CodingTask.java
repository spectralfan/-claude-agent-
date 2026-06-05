package com.kama.jchatmind.coding.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Coding 任务实体，对应表 t_coding_task。
 */
@Data
@Builder
public class CodingTask {

    private String id;

    private String sessionId;

    private String agentId;

    /** pending/running/completed/failed/timeout/waiting_approval/rejected */
    private String status;

    private String workspacePath;

    /** 任务绑定的本地工作区根（规范化绝对路径） */
    private String workspaceRoot;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String command;

    private String resultSummary;

    /** JSON string */
    private String metadata;

    /** maven_command */
    private String pendingAction;

    /** JSON string */
    private String pendingPayload;

    private String approvalReason;
}
