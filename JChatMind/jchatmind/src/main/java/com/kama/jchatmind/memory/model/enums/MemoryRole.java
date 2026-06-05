package com.kama.jchatmind.memory.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 记忆条目的角色，与对话消息的 role 对齐。
 */
@Getter
@AllArgsConstructor
public enum MemoryRole {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");

    @JsonValue
    private final String code;

    public static MemoryRole fromCode(String code) {
        for (MemoryRole value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid memory role: " + code);
    }
}
