package com.kama.jchatmind.coding.config;

import java.util.Locale;

public enum VerifyBackend {
    MCP,
    SANDBOX,
    AUTO;

    public static VerifyBackend fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mcp" -> MCP;
            case "sandbox" -> SANDBOX;
            default -> AUTO;
        };
    }
}
