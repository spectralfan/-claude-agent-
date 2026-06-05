package com.kama.jchatmind.memory.model.dto;

import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆查询出参。
 */
@Data
@Builder
public class MemoryResultDTO {

    private String id;

    private String sessionId;

    private MemoryType memoryType;

    private MemoryRole role;

    private String content;

    private String summary;

    private Integer importance;

    private List<String> tags;

    /** 相似度得分（仅向量召回有意义，越大越相关） */
    private Double score;

    private LocalDateTime createdAt;
}
