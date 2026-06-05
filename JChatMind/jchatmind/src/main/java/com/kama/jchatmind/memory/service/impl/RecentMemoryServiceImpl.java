package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.RecentMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecentMemoryServiceImpl implements RecentMemoryService {

    private final MemoryEntryMapper memoryEntryMapper;
    private final MemoryProperties properties;

    @Override
    public List<MemoryEntry> topByImportance(String sessionId, Integer minImportance, int limit) {
        return memoryEntryMapper.selectByImportance(
                sessionId, MemoryType.RECENT.getCode(), minImportance, limit);
    }

    @Override
    public void promote(String memoryId) {
        memoryEntryMapper.updateMemoryType(memoryId, MemoryType.RECENT.getCode());
    }

    @Override
    public List<MemoryEntry> idleCandidates(String sessionId, int limit) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(properties.getRecentIdleMinutes());
        return memoryEntryMapper.selectIdleByType(
                sessionId, MemoryType.RECENT.getCode(), before, limit);
    }

    @Override
    public long count(String sessionId) {
        return memoryEntryMapper.countByType(sessionId, MemoryType.RECENT.getCode());
    }
}
