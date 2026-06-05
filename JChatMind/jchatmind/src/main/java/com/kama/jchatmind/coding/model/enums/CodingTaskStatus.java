package com.kama.jchatmind.coding.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CodingTaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    TIMEOUT("timeout"),
    WAITING_APPROVAL("waiting_approval"),
    REJECTED("rejected");

    @JsonValue
    private final String code;
}
