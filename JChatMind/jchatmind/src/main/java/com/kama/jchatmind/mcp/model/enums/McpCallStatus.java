package com.kama.jchatmind.mcp.model.enums;

/**
 * MCP 工具调用状态，存库为小写 code。
 */
public enum McpCallStatus {

    SUCCESS("success"),
    FAILED("failed");

    private final String code;

    McpCallStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
