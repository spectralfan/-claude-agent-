package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记忆统计实体，对应表 t_memory_stats。
 * 唯一约束：(session_id, stat_date, memory_type)
 */
@Data
@Builder
public class MemoryStats {

    private String id;

    private String sessionId;

    private LocalDate statDate;

    private String memoryType;

    private Integer entryCount;

    private Integer totalTokens;

    private Integer queryCount;

    private Integer hitCount;

    /** JSON string */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
