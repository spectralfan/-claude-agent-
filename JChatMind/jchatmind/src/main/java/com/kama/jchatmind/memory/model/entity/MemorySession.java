package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆会话实体，对应表 t_memory_session。
 */
@Data
@Builder
public class MemorySession {

    private String id;

    private String sessionId;

    private String agentId;

    private String userId;

    /** active / closed 等 */
    private String status;

    private Integer totalMessages;

    private Integer totalTokens;

    private LocalDateTime lastActivityAt;

    /** JSON string */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
