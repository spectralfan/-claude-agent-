package com.kama.jchatmind.memory.mapper;

import com.kama.jchatmind.memory.model.entity.MemoryEmbedding;
import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryEmbeddingMapper {

    int insert(MemoryEmbedding embedding);

    MemoryEmbedding selectByContentHash(@Param("contentHash") String contentHash);

    long countByContentHash(@Param("contentHash") String contentHash);

    /**
     * 基于 cosine 距离的向量相似度检索，返回关联的记忆条目（按距离升序，即最相关在前）。
     */
    List<MemoryEntry> similaritySearch(@Param("sessionId") String sessionId,
                                       @Param("vectorLiteral") String vectorLiteral,
                                       @Param("limit") int limit);
}
