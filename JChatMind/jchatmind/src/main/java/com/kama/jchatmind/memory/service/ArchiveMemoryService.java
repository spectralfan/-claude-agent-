package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.entity.MemoryEmbedding;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;

import java.util.List;

/**
 * 长期归档记忆（ARCHIVE），含向量索引与语义检索。
 */
public interface ArchiveMemoryService {

    /**
     * 归档：标记为 ARCHIVE 并生成向量索引。
     */
    void archive(MemoryEntry entry);

    /**
     * 为记忆条目生成向量（SHA-256 去重）。已存在则返回 null。
     */
    MemoryEmbedding generateEmbedding(MemoryEntry entry);

    /**
     * 向量语义检索，返回最相关的归档记忆条目。
     */
    List<MemoryEntry> semanticSearch(String sessionId, String query, int limit);
}
