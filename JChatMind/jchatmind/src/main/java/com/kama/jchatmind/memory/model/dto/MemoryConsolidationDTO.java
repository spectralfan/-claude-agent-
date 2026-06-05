package com.kama.jchatmind.memory.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆整理任务入参。
 */
@Data
@Builder
public class MemoryConsolidationDTO {

    private String sessionId;

    /** 触发原因：SESSION_END / RECENT_OVERFLOW / MANUAL */
    private String trigger;

    /** 单次整理处理的最大条目数 */
    @Builder.Default
    private Integer maxEntries = 50;
}
