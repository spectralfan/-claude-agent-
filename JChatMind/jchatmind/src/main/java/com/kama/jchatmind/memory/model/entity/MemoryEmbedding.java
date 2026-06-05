package com.kama.jchatmind.memory.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆向量实体，对应表 t_memory_embedding。
 */
@Data
@Builder
public class MemoryEmbedding {

    private String id;

    private String memoryEntryId;

    private String sessionId;

    /** SHA-256，用于向量化去重 */
    private String contentHash;

    private String embeddingModel;

    /** pgvector，维度由 MemoryProperties 配置 */
    private float[] embedding;

    private LocalDateTime createdAt;
}
