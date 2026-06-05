package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.converter.MemoryMessageConverter;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.mapper.MemorySessionMapper;
import com.kama.jchatmind.memory.model.dto.MemoryConsolidationDTO;
import com.kama.jchatmind.memory.model.dto.MemoryQueryDTO;
import com.kama.jchatmind.memory.model.dto.MemoryResultDTO;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.dto.MemoryStatsDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.entity.MemorySession;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.ArchiveMemoryService;
import com.kama.jchatmind.memory.service.MemorySelector;
import com.kama.jchatmind.memory.service.MemoryService;
import com.kama.jchatmind.memory.service.MemoryTaskService;
import com.kama.jchatmind.memory.service.RecentMemoryService;
import com.kama.jchatmind.memory.service.WorkingMemoryService;
import com.kama.jchatmind.memory.support.MemorySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final WorkingMemoryService workingMemoryService;
    private final RecentMemoryService recentMemoryService;
    private final ArchiveMemoryService archiveMemoryService;
    private final MemorySelector memorySelector;
    private final MemoryMessageConverter messageConverter;
    private final MemoryEntryMapper memoryEntryMapper;
    private final MemorySessionMapper memorySessionMapper;
    private final MemoryTaskService memoryTaskService;
    private final MemoryProperties properties;
    private final MemorySupport support;

    @Override
    @Transactional
    public MemoryEntry save(MemorySaveDTO dto) {
        getOrCreateSession(dto.getSessionId(), dto.getAgentId());
        MemoryEntry entry = workingMemoryService.save(dto);

        int tokens = dto.getTokenCount() != null ? dto.getTokenCount() : support.estimateTokens(dto.getContent());
        memorySessionMapper.incrementCounters(dto.getSessionId(), 1, tokens);
        return entry;
    }

    @Override
    @Transactional
    public List<MemoryEntry> saveBatch(List<MemorySaveDTO> dtos) {
        List<MemoryEntry> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(dtos)) {
            return result;
        }
        for (MemorySaveDTO dto : dtos) {
            result.add(save(dto));
        }
        return result;
    }

    @Override
    public void updateSummary(String memoryId, String summary) {
        memoryEntryMapper.updateSummary(memoryId, summary);
    }

    @Override
    public void markImportance(String memoryId, int score, List<String> tags) {
        memoryEntryMapper.updateImportanceAndTags(memoryId, score, tags);
    }

    @Override
    public void archive(String memoryId) {
        MemoryEntry entry = memoryEntryMapper.selectById(memoryId);
        if (entry != null) {
            archiveMemoryService.archive(entry);
        }
    }

    @Override
    @Transactional
    public void archiveBatch(List<String> memoryIds) {
        if (CollectionUtils.isEmpty(memoryIds)) {
            return;
        }
        for (String id : memoryIds) {
            archive(id);
        }
    }

    @Override
    public List<MemoryResultDTO> query(MemoryQueryDTO query) {
        List<MemoryResultDTO> results = new ArrayList<>();
        if (query == null || !StringUtils.hasText(query.getSessionId())) {
            return results;
        }
        List<MemoryType> types = CollectionUtils.isEmpty(query.getTypes())
                ? List.of(MemoryType.WORKING, MemoryType.RECENT, MemoryType.ARCHIVE)
                : query.getTypes();
        int limit = query.getLimit() == null ? 20 : query.getLimit();

        if (types.contains(MemoryType.WORKING)) {
            for (MemoryEntry e : workingMemoryService.window(query.getSessionId(), limit)) {
                results.add(toResultDTO(e, null));
            }
        }
        if (types.contains(MemoryType.RECENT)) {
            for (MemoryEntry e : recentMemoryService.topByImportance(
                    query.getSessionId(), query.getMinImportance(), limit)) {
                results.add(toResultDTO(e, null));
            }
        }
        if (types.contains(MemoryType.ARCHIVE) && StringUtils.hasText(query.getQuery())) {
            List<MemoryEntry> hits = archiveMemoryService.semanticSearch(query.getSessionId(), query.getQuery(), limit);
            for (int i = 0; i < hits.size(); i++) {
                // 按检索排名近似得分（最相关在前），范围 (0,1]
                double score = 1.0 - ((double) i / Math.max(1, hits.size()));
                results.add(toResultDTO(hits.get(i), score));
            }
        }
        return results;
    }

    @Override
    public List<Message> buildContextMessages(String sessionId, int maxTokens) {
        int budget = maxTokens > 0
                ? maxTokens
                : (int) (properties.getDefaultContextWindow() * properties.getTokenBudgetRatio());
        String currentQuery = latestUserContent(sessionId);
        List<MemoryEntry> entries = memorySelector.selectMemories(sessionId, currentQuery, budget);
        return messageConverter.toMessages(entries);
    }

    @Override
    @Transactional
    public MemorySession getOrCreateSession(String sessionId, String agentId) {
        MemorySession existing = memorySessionMapper.selectBySessionId(sessionId);
        if (existing != null) {
            return existing;
        }
        MemorySession session = MemorySession.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .status("active")
                .totalMessages(0)
                .totalTokens(0)
                .build();
        memorySessionMapper.insert(session);
        return memorySessionMapper.selectBySessionId(sessionId);
    }

    @Override
    public void updateSessionActivity(String sessionId) {
        memorySessionMapper.updateActivity(sessionId, LocalDateTime.now());
    }

    @Override
    public MemoryStatsDTO getStats(String sessionId) {
        long working = memoryEntryMapper.countByType(sessionId, MemoryType.WORKING.getCode());
        long recent = memoryEntryMapper.countByType(sessionId, MemoryType.RECENT.getCode());
        long archive = memoryEntryMapper.countByType(sessionId, MemoryType.ARCHIVE.getCode());
        MemorySession session = memorySessionMapper.selectBySessionId(sessionId);
        return MemoryStatsDTO.builder()
                .sessionId(sessionId)
                .workingCount(working)
                .recentCount(recent)
                .archiveCount(archive)
                .totalCount(working + recent + archive)
                .totalTokens(session != null && session.getTotalTokens() != null ? session.getTotalTokens() : 0L)
                .totalMessages(session != null ? session.getTotalMessages() : 0)
                .build();
    }

    @Override
    public void triggerConsolidation(String sessionId) {
        MemoryConsolidationDTO input = MemoryConsolidationDTO.builder()
                .sessionId(sessionId)
                .trigger("MANUAL")
                .build();
        memoryTaskService.createConsolidationTask(input);
    }

    // ---------- helpers ----------

    private String latestUserContent(String sessionId) {
        List<MemoryEntry> window = workingMemoryService.window(sessionId, properties.getWorkingWindowSize());
        for (int i = window.size() - 1; i >= 0; i--) {
            MemoryEntry e = window.get(i);
            if (MemoryRole.USER.getCode().equalsIgnoreCase(e.getRole()) && StringUtils.hasText(e.getContent())) {
                return e.getContent();
            }
        }
        return window.isEmpty() ? null : window.get(window.size() - 1).getContent();
    }

    private MemoryResultDTO toResultDTO(MemoryEntry entry, Double score) {
        return MemoryResultDTO.builder()
                .id(entry.getId())
                .sessionId(entry.getSessionId())
                .memoryType(entry.getMemoryType() != null ? MemoryType.fromCode(entry.getMemoryType()) : null)
                .role(entry.getRole() != null ? MemoryRole.fromCode(entry.getRole()) : null)
                .content(entry.getContent())
                .summary(entry.getSummary())
                .importance(entry.getImportance())
                .tags(entry.getMemoryTags())
                .score(score)
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
