package com.kama.jchatmind.memory.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 记忆异步任务状态。
 */
@Getter
@AllArgsConstructor
public enum MemoryTaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    @JsonValue
    private final String code;

    public static MemoryTaskStatus fromCode(String code) {
        for (MemoryTaskStatus value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid memory task status: " + code);
    }
}
