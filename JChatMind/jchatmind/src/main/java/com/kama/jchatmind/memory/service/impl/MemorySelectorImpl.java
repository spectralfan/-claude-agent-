package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.service.ArchiveMemoryService;
import com.kama.jchatmind.memory.service.MemorySelector;
import com.kama.jchatmind.memory.service.RecentMemoryService;
import com.kama.jchatmind.memory.service.WorkingMemoryService;
import com.kama.jchatmind.memory.support.MemorySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆选择器实现：意图识别 + 分层召回 + token 预算融合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySelectorImpl implements MemorySelector {

    private final WorkingMemoryService workingMemoryService;
    private final RecentMemoryService recentMemoryService;
    private final ArchiveMemoryService archiveMemoryService;
    private final MemoryProperties properties;
    private final MemorySupport support;

    /** 暗示需要召回历史归档的意图关键词 */
    private static final String[] HISTORY_HINTS = {
            "之前", "上次", "以前", "历史", "先前", "早前", "那个项目", "回顾", "记得",
            "previously", "earlier", "last time", "before", "history", "recall"
    };

    @Override
    public List<MemoryEntry> selectMemories(String sessionId, String currentQuery, int maxTokens) {
        if (maxTokens <= 0) {
            maxTokens = (int) (properties.getDefaultContextWindow() * properties.getTokenBudgetRatio());
        }

        boolean wantArchive = StringUtils.hasText(currentQuery) && needArchive(currentQuery);

        // 分层 token 预算分配：WORKING 优先，其次 RECENT，最后 ARCHIVE
        int workingBudget = (int) (maxTokens * 0.5);
        int recentBudget = (int) (maxTokens * 0.3);
        int archiveBudget = maxTokens - workingBudget - recentBudget;

        Map<String, MemoryEntry> selected = new LinkedHashMap<>();
        int[] used = {0};

        // 1. WORKING 滑动窗口（最近 N 条）
        List<MemoryEntry> working = workingMemoryService.window(sessionId, properties.getWorkingWindowSize());
        fill(selected, working, workingBudget, maxTokens, used);

        // 2. RECENT 按重要性
        List<MemoryEntry> recent = recentMemoryService.topByImportance(sessionId, null, properties.getRecentMaxEntries());
        fill(selected, recent, workingBudget + recentBudget, maxTokens, used);

        // 3. ARCHIVE 向量召回（仅在意图涉及历史时）
        if (wantArchive && archiveBudget > 0) {
            List<MemoryEntry> archive = archiveMemoryService.semanticSearch(sessionId, currentQuery, 10);
            fill(selected, archive, maxTokens, maxTokens, used);
        }

        // 融合后按时间正序，保证对话连贯
        List<MemoryEntry> result = new ArrayList<>(selected.values());
        result.sort(Comparator.comparing(
                MemoryEntry::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        log.debug("记忆召回完成 session={} 命中 {} 条, 估算 tokens={}", sessionId, result.size(), used[0]);
        return result;
    }

    @Override
    public List<MemoryEntry> selectSupplementalMemories(String sessionId, String currentQuery, int maxTokens) {
        if (maxTokens <= 0) {
            maxTokens = (int) (properties.getDefaultContextWindow() * properties.getTokenBudgetRatio() * 0.3);
        }
        boolean wantArchive = StringUtils.hasText(currentQuery) && needArchive(currentQuery);
        int recentBudget = (int) (maxTokens * 0.6);
        int archiveBudget = maxTokens - recentBudget;

        Map<String, MemoryEntry> selected = new LinkedHashMap<>();
        int[] used = {0};

        List<MemoryEntry> recent = recentMemoryService.topByImportance(sessionId, null, properties.getRecentMaxEntries());
        fill(selected, recent, recentBudget, maxTokens, used);

        if (wantArchive && archiveBudget > 0) {
            List<MemoryEntry> archive = archiveMemoryService.semanticSearch(sessionId, currentQuery, 10);
            fill(selected, archive, maxTokens, maxTokens, used);
        }

        List<MemoryEntry> result = new ArrayList<>(selected.values());
        result.sort(Comparator.comparing(
                MemoryEntry::getCreatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return result;
    }

    /**
     * 在不超过 tierCap（该层累计上限）与 hardCap（总预算）的前提下，将候选填入已选集合。
     */
    private void fill(Map<String, MemoryEntry> selected, List<MemoryEntry> candidates,
                      int tierCap, int hardCap, int[] used) {
        if (candidates == null) {
            return;
        }
        for (MemoryEntry entry : candidates) {
            if (entry.getId() != null && selected.containsKey(entry.getId())) {
                continue;
            }
            String text = StringUtils.hasText(entry.getSummary()) ? entry.getSummary() : entry.getContent();
            int tokens = support.estimateTokens(text);
            if (used[0] + tokens > tierCap || used[0] + tokens > hardCap) {
                if (used[0] >= hardCap) {
                    break;
                }
                continue;
            }
            selected.put(entry.getId(), entry);
            used[0] += tokens;
        }
    }

    private boolean needArchive(String query) {
        String lower = query.toLowerCase();
        for (String hint : HISTORY_HINTS) {
            if (query.contains(hint) || lower.contains(hint.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void adjustTiers(String sessionId) {
        List<MemoryEntry> idle = recentMemoryService.idleCandidates(sessionId, properties.getRecentMaxEntries());
        if (idle.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        log.debug("session={} 将 {} 条空闲 RECENT 归档到 ARCHIVE (基准时间 {})", sessionId, idle.size(), now);
        for (MemoryEntry entry : idle) {
            archiveMemoryService.archive(entry);
        }
    }
}
