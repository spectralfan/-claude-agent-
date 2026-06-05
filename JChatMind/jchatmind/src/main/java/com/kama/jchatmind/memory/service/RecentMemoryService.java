package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.entity.MemoryEntry;

import java.util.List;

/**
 * 中期记忆（RECENT），按重要性保留。
 */
public interface RecentMemoryService {

    /**
     * 按重要性召回 top N。
     */
    List<MemoryEntry> topByImportance(String sessionId, Integer minImportance, int limit);

    /**
     * 将指定记忆标记为 RECENT。
     */
    void promote(String memoryId);

    /**
     * 取长期未访问的 RECENT 条目（归档候选）。
     */
    List<MemoryEntry> idleCandidates(String sessionId, int limit);

    long count(String sessionId);
}
