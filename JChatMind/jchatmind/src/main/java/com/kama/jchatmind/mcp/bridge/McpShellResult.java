package com.kama.jchatmind.mcp.bridge;

public record McpShellResult(int exitCode, String output) {

    public boolean success() {
        return exitCode == 0;
    }
}
