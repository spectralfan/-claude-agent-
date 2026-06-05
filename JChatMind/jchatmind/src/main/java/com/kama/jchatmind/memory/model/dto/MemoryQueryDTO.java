package com.kama.jchatmind.memory.model.dto;

import com.kama.jchatmind.memory.model.enums.MemoryType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 记忆查询入参。
 */
@Data
@Builder
public class MemoryQueryDTO {

    private String sessionId;

    /** 当前查询/意图文本，用于 ARCHIVE 向量召回 */
    private String query;

    /** 限定召回的记忆层级；为空表示全部 */
    private List<MemoryType> types;

    /** token 预算，影响融合后返回条目数 */
    private Integer maxTokens;

    /** 每层最大条目数 */
    @Builder.Default
    private Integer limit = 20;

    /** RECENT 召回的最小重要性阈值 */
    private Integer minImportance;
}
