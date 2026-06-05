package com.kama.jchatmind.memory.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 记忆异步任务类型。
 * CONSOLIDATION  记忆整理（读取 RECENT、生成摘要、归档）
 * SUMMARIZATION  仅摘要生成
 * INDEXING       向量索引构建
 */
@Getter
@AllArgsConstructor
public enum MemoryTaskType {
    CONSOLIDATION("consolidation"),
    SUMMARIZATION("summarization"),
    INDEXING("indexing");

    @JsonValue
    private final String code;

    public static MemoryTaskType fromCode(String code) {
        for (MemoryTaskType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid memory task type: " + code);
    }
}
