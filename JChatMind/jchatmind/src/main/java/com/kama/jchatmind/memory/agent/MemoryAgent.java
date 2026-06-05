package com.kama.jchatmind.memory.agent;

import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.mapper.MemoryTaskMapper;
import com.kama.jchatmind.memory.model.dto.MemoryConsolidationDTO;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.model.entity.MemoryTask;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryTaskStatus;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import com.kama.jchatmind.memory.service.ArchiveMemoryService;
import com.kama.jchatmind.memory.service.RecentMemoryService;
import com.kama.jchatmind.memory.support.MemorySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆整理子 Agent：异步执行记忆整理任务。
 *
 * <p>独立的 ChatClient 调用，不与主 Think-Execute 共享上下文。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryAgent {

    private static final int CONSOLIDATED_IMPORTANCE = 8;
    private static final int MAX_SUMMARY_INPUT_CHARS = 6000;

    private final MemoryTaskMapper memoryTaskMapper;
    private final MemoryEntryMapper memoryEntryMapper;
    private final RecentMemoryService recentMemoryService;
    private final ArchiveMemoryService archiveMemoryService;
    private final ChatClientRegistry chatClientRegistry;
    private final MemoryProperties properties;
    private final MemorySupport support;
    private final ObjectMapper objectMapper;

    /**
     * 异步执行一个记忆整理任务。任务在被分发前应已被标记为 running。
     */
    @Async("taskExecutor")
    public void execute(MemoryTask task) {
        String taskId = task.getId();
        try {
            MemoryConsolidationDTO input = parseInput(task);
            Map<String, Object> result = consolidate(input);
            memoryTaskMapper.updateStatus(
                    taskId,
                    MemoryTaskStatus.COMPLETED.getCode(),
                    null,
                    LocalDateTime.now(),
                    support.toJson(result),
                    null);
            log.info("记忆整理任务完成 taskId={} result={}", taskId, result);
        } catch (Exception e) {
            log.error("记忆整理任务失败 taskId={}", taskId, e);
            memoryTaskMapper.updateStatus(
                    taskId,
                    MemoryTaskStatus.FAILED.getCode(),
                    null,
                    LocalDateTime.now(),
                    null,
                    e.getMessage());
        }
    }

    private MemoryConsolidationDTO parseInput(MemoryTask task) {
        if (StringUtils.hasText(task.getInputData())) {
            try {
                return objectMapper.readValue(task.getInputData(), MemoryConsolidationDTO.class);
            } catch (Exception e) {
                log.warn("解析整理任务入参失败，使用默认值: {}", e.getMessage());
            }
        }
        return MemoryConsolidationDTO.builder().sessionId(task.getSessionId()).trigger("AUTO").build();
    }

    /**
     * 整理流程：读取 RECENT -> 生成摘要 -> 写入归档摘要 -> 向量化 -> 归档原条目 -> 清理 WORKING。
     */
    public Map<String, Object> consolidate(MemoryConsolidationDTO input) {
        String sessionId = input.getSessionId();
        int maxEntries = input.getMaxEntries() == null ? properties.getRecentMaxEntries() : input.getMaxEntries();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        List<MemoryEntry> recent = recentMemoryService.topByImportance(sessionId, null, maxEntries);
        if (recent.isEmpty()) {
            result.put("recentProcessed", 0);
            result.put("note", "no recent memory to consolidate");
            return result;
        }

        String summary = summarize(recent);

        // 1. 写入一条整理摘要（归档层）并向量化
        MemoryEntry consolidated = MemoryEntry.builder()
                .sessionId(sessionId)
                .memoryType(MemoryType.ARCHIVE.getCode())
                .role(MemoryRole.SYSTEM.getCode())
                .content(summary)
                .summary(summary)
                .importance(CONSOLIDATED_IMPORTANCE)
                .memoryTags(List.of("summary", "consolidation"))
                .build();
        memoryEntryMapper.insert(consolidated);
        try {
            archiveMemoryService.generateEmbedding(consolidated);
        } catch (Exception e) {
            log.warn("整理摘要向量化失败 session={}: {}", sessionId, e.getMessage());
        }

        // 2. 归档原 RECENT 条目（含各自向量化，SHA-256 去重）
        int archived = 0;
        for (MemoryEntry entry : recent) {
            archiveMemoryService.archive(entry);
            archived++;
        }

        // 3. 清理过期 WORKING 条目（保留最近 N 条）
        int cleaned = cleanupWorking(sessionId);

        result.put("recentProcessed", recent.size());
        result.put("archived", archived);
        result.put("workingCleaned", cleaned);
        result.put("summaryChars", summary.length());
        return result;
    }

    private int cleanupWorking(String sessionId) {
        List<MemoryEntry> working = memoryEntryMapper.selectRecentByType(
                sessionId, MemoryType.WORKING.getCode(), 1000);
        int window = properties.getWorkingWindowSize();
        if (working.size() <= window) {
            return 0;
        }
        // selectRecentByType 返回时间正序，删除最旧的 (size - window) 条
        int toDelete = working.size() - window;
        int deleted = 0;
        for (int i = 0; i < toDelete; i++) {
            memoryEntryMapper.deleteById(working.get(i).getId());
            deleted++;
        }
        return deleted;
    }

    private String summarize(List<MemoryEntry> entries) {
        StringBuilder dialogue = new StringBuilder();
        for (MemoryEntry entry : entries) {
            String text = StringUtils.hasText(entry.getSummary()) ? entry.getSummary() : entry.getContent();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            dialogue.append('[').append(entry.getRole()).append("] ").append(text).append('\n');
            if (dialogue.length() > MAX_SUMMARY_INPUT_CHARS) {
                break;
            }
        }

        ChatClient chatClient = chatClientRegistry.get(properties.getConsolidationModel());
        if (chatClient == null) {
            log.warn("未找到整理模型 {}，退化为截断拼接摘要", properties.getConsolidationModel());
            return fallbackSummary(dialogue.toString());
        }
        try {
            String sys = "你是记忆整理助手。请将下面的多轮对话片段浓缩为一段简洁的中文摘要，"
                    + "突出：完成了哪些任务、关键决策、涉及的文件或工具、待跟进事项。只输出摘要本身，不要解释。";
            String content = chatClient.prompt().system(sys).user(dialogue.toString()).call().content();
            if (StringUtils.hasText(content)) {
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("LLM 生成摘要失败，退化为截断拼接: {}", e.getMessage());
        }
        return fallbackSummary(dialogue.toString());
    }

    private String fallbackSummary(String dialogue) {
        String prefix = "[自动摘要] 本轮对话要点：";
        int limit = 480;
        String body = dialogue.length() > limit ? dialogue.substring(0, limit) + "..." : dialogue;
        return prefix + body.replace('\n', ' ');
    }
}
