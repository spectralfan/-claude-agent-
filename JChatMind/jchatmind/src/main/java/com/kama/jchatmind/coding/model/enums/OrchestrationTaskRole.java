package com.kama.jchatmind.coding.model.enums;

import lombok.Getter;

@Getter
public enum OrchestrationTaskRole {
    WORKER("WORKER"),
    REVIEWER("REVIEWER");

    private final String code;

    OrchestrationTaskRole(String code) {
        this.code = code;
    }

    public static OrchestrationTaskRole fromCode(String code) {
        if (code == null) {
            return WORKER;
        }
        for (OrchestrationTaskRole role : values()) {
            if (role.code.equalsIgnoreCase(code.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知编排角色: " + code);
    }
}
