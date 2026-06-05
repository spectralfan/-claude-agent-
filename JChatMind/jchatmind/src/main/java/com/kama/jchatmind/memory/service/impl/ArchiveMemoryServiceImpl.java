package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryEmbeddingMapper;
import com.kama.jchatmind.memory.mapper.MemoryEntryMapper;
import com.kama.jchatmind.memory.model.entity.MemoryEmbedding;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import com.kama.jchatmind.memory.service.ArchiveMemoryService;
import com.kama.jchatmind.memory.support.MemorySupport;
import com.kama.jchatmind.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveMemoryServiceImpl implements ArchiveMemoryService {

    private final MemoryEntryMapper memoryEntryMapper;
    private final MemoryEmbeddingMapper memoryEmbeddingMapper;
    private final MemoryProperties properties;
    private final MemorySupport support;
    private final RagService ragService;

    @Override
    @Transactional
    public void archive(MemoryEntry entry) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        memoryEntryMapper.archive(entry.getId(), LocalDateTime.now());
        try {
            generateEmbedding(entry);
        } catch (Exception e) {
            log.warn("归档记忆 {} 生成向量失败: {}", entry.getId(), e.getMessage());
        }
    }

    @Override
    public MemoryEmbedding generateEmbedding(MemoryEntry entry) {
        String text = StringUtils.hasText(entry.getSummary()) ? entry.getSummary() : entry.getContent();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String hash = support.sha256(text);
        if (memoryEmbeddingMapper.countByContentHash(hash) > 0) {
            log.debug("内容哈希 {} 已存在向量，跳过重复向量化", hash);
            return null;
        }

        float[] vector = ragService.embed(text);
        MemoryEmbedding embedding = MemoryEmbedding.builder()
                .memoryEntryId(entry.getId())
                .sessionId(entry.getSessionId())
                .contentHash(hash)
                .embeddingModel(properties.getEmbeddingModel())
                .embedding(vector)
                .build();
        memoryEmbeddingMapper.insert(embedding);
        return embedding;
    }

    @Override
    public List<MemoryEntry> semanticSearch(String sessionId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        float[] queryVector = ragService.embed(query);
        String literal = support.toVectorLiteral(queryVector);
        return memoryEmbeddingMapper.similaritySearch(sessionId, literal, limit);
    }
}
