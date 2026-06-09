package com.kama.jchatmind.coding.model.enums;

import lombok.Getter;

@Getter
public enum OrchestrationTaskStatus {
    PENDING("PENDING"),
    READY("READY"),
    RUNNING("RUNNING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    OrchestrationTaskStatus(String code) {
        this.code = code;
    }

    public static OrchestrationTaskStatus fromCode(String code) {
        if (code == null) {
            return PENDING;
        }
        for (OrchestrationTaskStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
