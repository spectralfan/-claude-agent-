package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.entity.MemoryEntry;

import java.util.List;

/**
 * 记忆智能选择器：根据当前上下文分层召回并按 token 预算融合。
 */
public interface MemorySelector {

    /**
     * 根据当前查询智能选择记忆（WORKING 滑窗 + RECENT 重要性 + ARCHIVE 向量召回）。
     * 返回结果已按时间正序排列，总 token 数不超过 maxTokens。
     */
    List<MemoryEntry> selectMemories(String sessionId, String currentQuery, int maxTokens);

    /**
     * 动态调整记忆层级：将长期未访问的 RECENT 归档到 ARCHIVE。
     */
    void adjustTiers(String sessionId);
}
