package com.kama.jchatmind.memory.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆统计出参。
 */
@Data
@Builder
public class MemoryStatsDTO {

    private String sessionId;

    private long workingCount;

    private long recentCount;

    private long archiveCount;

    private long totalCount;

    private long totalTokens;

    private Integer totalMessages;
}
