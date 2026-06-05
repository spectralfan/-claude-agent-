package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.MemoryImportanceService;
import com.kama.jchatmind.memory.service.WorkingMemoryService;
import com.kama.jchatmind.memory.support.MemorySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingMemoryServiceImpl implements WorkingMemoryService {

    private final MemoryEntryMapper memoryEntryMapper;
    private final MemoryImportanceService importanceService;
    private final MemoryProperties properties;
    private final MemorySupport support;

    @Override
    @Transactional
    public MemoryEntry save(MemorySaveDTO dto) {
        int importance = dto.getImportance() != null
                ? dto.getImportance()
                : importanceService.evaluate(dto);

        List<String> tags = !CollectionUtils.isEmpty(dto.getTags())
                ? dto.getTags()
                : importanceService.extractTags(dto);

        MemoryType targetType = dto.getMemoryType() == null ? MemoryType.WORKING : dto.getMemoryType();
        // 重要性达到阈值的工作记忆直接升级到 RECENT
        if (targetType == MemoryType.WORKING && importance >= properties.getRecentImportanceThreshold()) {
            targetType = MemoryType.RECENT;
            log.debug("记忆重要性 {} 达到阈值，直接落到 RECENT 层", importance);
        }

        MemoryRole role = dto.getRole() == null ? MemoryRole.USER : dto.getRole();

        MemoryEntry entry = MemoryEntry.builder()
                .sessionId(dto.getSessionId())
                .memoryType(targetType.getCode())
                .role(role.getCode())
                .content(dto.getContent() == null ? "" : dto.getContent())
                .importance(importance)
                .memoryTags(CollectionUtils.isEmpty(tags) ? null : tags)
                .toolCalls(support.toJson(dto.getToolCalls()))
                .metadata(support.toJson(dto.getMetadata()))
                .build();

        memoryEntryMapper.insert(entry);
        return entry;
    }

    @Override
    public List<MemoryEntry> window(String sessionId, int limit) {
        return memoryEntryMapper.selectRecentByType(sessionId, MemoryType.WORKING.getCode(), limit);
    }
}
