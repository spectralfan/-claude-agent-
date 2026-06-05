package com.kama.jchatmind.coding.model.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MavenGoal {
    COMPILE("compile"),
    TEST("test"),
    TEST_SINGLE("test_single"),
    PACKAGE_SKIP_TESTS("package_skip_tests"),
    CLEAN_COMPILE("clean_compile"),
    CLEAN_TEST("clean_test");

    @JsonValue
    private final String code;
}
