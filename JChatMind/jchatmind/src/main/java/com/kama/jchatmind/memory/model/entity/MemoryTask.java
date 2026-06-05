package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆异步任务实体，对应表 t_memory_task。
 */
@Data
@Builder
public class MemoryTask {

    private String id;

    /** consolidation / summarization / indexing */
    private String taskType;

    private String sessionId;

    /** pending / running / completed / failed */
    private String status;

    private Integer priority;

    /** JSON string */
    private String inputData;

    /** JSON string */
    private String resultData;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;
}
