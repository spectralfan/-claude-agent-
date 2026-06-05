package com.kama.jchatmind.memory.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 记忆分层类型。
 * WORKING  短期工作记忆（滑动窗口，最近若干条）
 * RECENT   中期记忆（按重要性保留）
 * ARCHIVE  长期归档记忆（向量化后可语义检索）
 */
@Getter
@AllArgsConstructor
public enum MemoryType {
    WORKING("WORKING"),
    RECENT("RECENT"),
    ARCHIVE("ARCHIVE");

    @JsonValue
    private final String code;

    public static MemoryType fromCode(String code) {
        for (MemoryType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid memory type: " + code);
    }
}
