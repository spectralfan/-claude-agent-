package com.kama.jchatmind.memory.model.dto;

import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.model.enums.MemoryType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 记忆保存入参。
 */
@Data
@Builder
public class MemorySaveDTO {

    private String sessionId;

    private String agentId;

    private MemoryRole role;

    private String content;

    /** 目标记忆层级，默认 WORKING */
    @Builder.Default
    private MemoryType memoryType = MemoryType.WORKING;

    /** 显式指定重要性；为 null 时由 MemoryImportanceService 自动评分 */
    private Integer importance;

    private List<String> tags;

    private List<ToolCallInfo> toolCalls;

    /** 附加元数据，序列化为 JSONB */
    private Map<String, Object> metadata;

    /** 估算 token 数，用于统计与预算 */
    private Integer tokenCount;
}
