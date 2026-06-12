package com.kama.jchatmind.agent.tools;

public class ToolResult {
    private final String content;
    private final boolean isError;
    private final String errorType;

    public ToolResult(String content) { this(content, false, null); }

    public ToolResult(String content, boolean isError, String errorType) {
        this.content = content;
        this.isError = isError;
        this.errorType = errorType;
    }

    public static ToolResult ok(String content) { return new ToolResult(content, false, null); }
    public static ToolResult error(String message) { return new ToolResult(message, true, "runtime_error"); }
    public static ToolResult error(String message, String errorType) { return new ToolResult(message, true, errorType); }

    public String getContent() { return content; }
    public boolean isError() { return isError; }
    public String getErrorType() { return errorType; }
}