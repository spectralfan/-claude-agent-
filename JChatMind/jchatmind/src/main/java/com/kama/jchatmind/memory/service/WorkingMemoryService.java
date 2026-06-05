package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;

import java.util.List;

/**
 * 短期工作记忆（WORKING）。
 */
public interface WorkingMemoryService {

    /**
     * 保存一条记忆：自动重要性评分；当分值达到阈值时直接落到 RECENT 层（升级）。
     */
    MemoryEntry save(MemorySaveDTO dto);

    /**
     * 滑动窗口：取最近 limit 条 WORKING 记忆（时间正序）。
     */
    List<MemoryEntry> window(String sessionId, int limit);
}
