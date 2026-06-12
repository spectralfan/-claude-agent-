package com.kama.jchatmind.mcp.bridge;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class McpToolAliasRegistryTest {
    @Test void resolveCanonicalName_bash_shouldReturnBash() {
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("bash"));
    }
    @Test void resolveCanonicalName_alias_shouldReturnBash() {
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("run_terminal_cmd"));
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("shell"));
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("shell_exec"));
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("shell_execute"));
        assertEquals("bash", McpToolAliasRegistry.resolveCanonicalName("execute_command"));
    }
    @Test void resolveCanonicalName_unknown_shouldReturnNull() {
        assertNull(McpToolAliasRegistry.resolveCanonicalName("unknown_tool"));
    }
    @Test void isTerminalToolName_shouldRecognizeAll() {
        assertTrue(McpToolAliasRegistry.isTerminalToolName("bash"));
        assertTrue(McpToolAliasRegistry.isTerminalToolName("run_terminal_cmd"));
        assertTrue(McpToolAliasRegistry.isTerminalToolName("shell"));
        assertFalse(McpToolAliasRegistry.isTerminalToolName("coding_file_tools"));
    }
}