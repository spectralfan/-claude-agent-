package com.kama.jchatmind.coding.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CodingActionType {
    MAVEN_COMMAND("maven_command");

    @JsonValue
    private final String code;
}
