package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpShellExecutorTest {

    @Test
    void execute_whenMcpDisabled_returnsEmpty() {
        McpProperties props = new McpProperties();
        props.setEnabled(false);
        McpShellExecutor executor = new McpShellExecutor(mock(McpToolBridge.class), props, new ObjectMapper());

        assertTrue(executor.execute("npm test", "/tmp").isEmpty());
    }

    @Test
    void execute_whenNoShellTool_returnsEmpty() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        McpToolBridge bridge = mock(McpToolBridge.class);
        when(bridge.getAllToolCallbacks()).thenReturn(List.of());
        McpShellExecutor executor = new McpShellExecutor(bridge, props, new ObjectMapper());

        assertTrue(executor.execute("npm test", "/tmp").isEmpty());
    }

    @Test
    void execute_success_parsesExitCode() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("execute_command");
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call(anyString())).thenReturn("exit code: 0\nall tests passed");

        McpToolBridge bridge = mock(McpToolBridge.class);
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(callback));

        McpShellExecutor executor = new McpShellExecutor(bridge, props, new ObjectMapper());
        Optional<McpShellResult> result = executor.execute("npm test", "Z:/workspace");

        assertTrue(result.isPresent());
        assertEquals(0, result.get().exitCode());
        assertTrue(result.get().output().contains("all tests passed"));
    }

    @Test
    void inferExitCode_detectsFailure() {
        assertEquals(1, McpShellExecutor.inferExitCode("MCP 命令被策略拦截\nexit code: 1"));
        assertEquals(0, McpShellExecutor.inferExitCode("exit code: 0\nOK"));
    }

    @Test
    void isAvailable_whenToolPresent_returnsTrue() {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("execute_command");
        when(callback.getToolDefinition()).thenReturn(definition);
        McpToolBridge bridge = mock(McpToolBridge.class);
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(callback));

        McpShellExecutor executor = new McpShellExecutor(bridge, props, new ObjectMapper());
        assertTrue(executor.isAvailable());
    }

    @Test
    void execute_whenCallbackThrows_returnsEmpty() throws Exception {
        McpProperties props = new McpProperties();
        props.setEnabled(true);
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("execute_command");
        when(callback.getToolDefinition()).thenReturn(definition);
        when(callback.call(anyString())).thenThrow(new RuntimeException("boom"));

        McpToolBridge bridge = mock(McpToolBridge.class);
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(callback));

        McpShellExecutor executor = new McpShellExecutor(bridge, props, new ObjectMapper());
        assertFalse(executor.execute("npm test", "/tmp").isPresent());
    }
}
