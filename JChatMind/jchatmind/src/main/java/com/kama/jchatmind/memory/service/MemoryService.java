package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.dto.MemoryQueryDTO;
import com.kama.jchatmind.memory.model.dto.MemoryResultDTO;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.dto.MemoryStatsDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.entity.MemorySession;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Memory Hub 核心门面。
 *
 * <p>说明：原始接口设计使用 {@code UUID} 作为记忆 ID，这里改用 {@code String}，
 * 与项目既有实体（id 均为 String）保持一致。</p>
 */
public interface MemoryService {

    // --- 写操作 ---

    MemoryEntry save(MemorySaveDTO dto);

    List<MemoryEntry> saveBatch(List<MemorySaveDTO> dtos);

    void updateSummary(String memoryId, String summary);

    void markImportance(String memoryId, int score, List<String> tags);

    void archive(String memoryId);

    void archiveBatch(List<String> memoryIds);

    // --- 查询操作 ---

    List<MemoryResultDTO> query(MemoryQueryDTO query);

    List<Message> buildContextMessages(String sessionId, int maxTokens);

    // --- 会话管理 ---

    MemorySession getOrCreateSession(String sessionId, String agentId);

    void updateSessionActivity(String sessionId);

    // --- 统计与任务 ---

    MemoryStatsDTO getStats(String sessionId);

    void triggerConsolidation(String sessionId);
}
