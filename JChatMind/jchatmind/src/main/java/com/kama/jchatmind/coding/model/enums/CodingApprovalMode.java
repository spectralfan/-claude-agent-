package com.kama.jchatmind.coding.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Claude Code 式命令审批模式。
 */
@Getter
@AllArgsConstructor
public enum CodingApprovalMode {
    /** 仅 compile 自动执行，其余需人工批准 */
    STRICT("strict"),
    /** 开发模式：compile/test 等白名单命令自动执行（推荐） */
    DEVELOPMENT("development"),
    /** 信任模式：所有白名单 Maven 命令自动执行 */
    TRUSTED("trusted");

    @JsonValue
    private final String code;

    @JsonCreator
    public static CodingApprovalMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (CodingApprovalMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的 approvalMode: " + code);
    }
}
