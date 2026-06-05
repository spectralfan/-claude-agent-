package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆上下文实体（键值型上下文），对应表 t_memory_context。
 */
@Data
@Builder
public class MemoryContext {

    private String id;

    private String sessionId;

    private String contextKey;

    private String contextValue;

    /** JSON string */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
