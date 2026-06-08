package com.kama.jchatmind.mcp;

import com.kama.jchatmind.mcp.bridge.McpCallRecorder;
import com.kama.jchatmind.mcp.bridge.McpToolAliasRegistry;
import com.kama.jchatmind.mcp.bridge.McpToolBridge;
import com.kama.jchatmind.mcp.bridge.RecordingToolCallback;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.mapper.McpToolCallMapper;
import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import com.kama.jchatmind.mcp.integration.McpIntegrationImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * MCP 模块纯逻辑单测（Mockito，无需 Spring 上下文 / 真实 MCP server）：
 * 覆盖埋点装饰、白名单过滤、用量统计。
 */
class McpModuleTest {

    private ToolCallback mockTool(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition td = mock(ToolDefinition.class);
        when(td.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(td);
        return cb;
    }

    @Test
    void recordingCallback_success_recordsAndReturnsOutput() {
        ToolCallback delegate = mockTool("github_create_issue");
        when(delegate.call("{\"x\":1}")).thenReturn("{\"ok\":true}");
        McpCallRecorder recorder = mock(McpCallRecorder.class);

        RecordingToolCallback rc = new RecordingToolCallback(delegate, "github", recorder, true);
        String out = rc.call("{\"x\":1}");

        assertEquals("{\"ok\":true}", out);
        ArgumentCaptor<McpToolCall> cap = ArgumentCaptor.forClass(McpToolCall.class);
        verify(recorder).record(cap.capture());
        McpToolCall rec = cap.getValue();
        assertEquals("github", rec.getServerId());
        assertEquals("github_create_issue", rec.getToolName());
        assertEquals("success", rec.getStatus());
    }

    @Test
    void recordingCallback_failure_recordsFailedAndReturnsErrorText() {
        ToolCallback delegate = mockTool("slack_post");
        when(delegate.call(anyString())).thenThrow(new RuntimeException("boom"));
        McpCallRecorder recorder = mock(McpCallRecorder.class);

        RecordingToolCallback rc = new RecordingToolCallback(delegate, "slack", recorder, true);
        String out = rc.call("{}");

        assertTrue(out.contains("调用失败"));
        assertTrue(out.contains("boom"));
        ArgumentCaptor<McpToolCall> cap = ArgumentCaptor.forClass(McpToolCall.class);
        verify(recorder).record(cap.capture());
        assertEquals("failed", cap.getValue().getStatus());
    }

    @Test
    void recordingCallback_disabledRecording_doesNotRecord() {
        ToolCallback delegate = mockTool("fs_read");
        when(delegate.call(anyString())).thenReturn("data");
        McpCallRecorder recorder = mock(McpCallRecorder.class);

        RecordingToolCallback rc = new RecordingToolCallback(delegate, "fs", recorder, false);
        rc.call("{}");

        verifyNoInteractions(recorder);
    }

    @Test
    void getToolsForAgent_shellExec_returnsCanonicalTool_only() {
        McpToolBridge bridge = mock(McpToolBridge.class);
        ToolCallback shellExec = mockTool("shell_exec");
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(shellExec));
        McpProperties props = new McpProperties();
        McpIntegrationImpl integration = new McpIntegrationImpl(bridge, props, mock(McpToolCallMapper.class));

        List<ToolCallback> picked = integration.getToolsForAgent(
                List.of("shell_exec", "run_terminal_cmd", "coding_file_tools"));

        assertEquals(1, picked.size());
        assertEquals("shell_exec", picked.get(0).getToolDefinition().name());
        List<ToolCallback> expanded = McpToolAliasRegistry.expandAliases(picked);
        assertTrue(expanded.stream().anyMatch(t -> "run_terminal_cmd".equals(t.getToolDefinition().name())));
    }

    @Test
    void getShellToolCallbacks_returnsAllShellTools() {
        McpToolBridge bridge = mock(McpToolBridge.class);
        ToolCallback executeCommand = mockTool("execute_command");
        ToolCallback other = mockTool("github_create_issue");
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(executeCommand, other));
        McpIntegrationImpl integration =
                new McpIntegrationImpl(bridge, new McpProperties(), mock(McpToolCallMapper.class));

        List<ToolCallback> shells = integration.getShellToolCallbacks();

        assertEquals(1, shells.size());
        assertEquals("execute_command", shells.get(0).getToolDefinition().name());
    }

    @Test
    void getToolsForAgent_filtersByAllowlist_withPrefixStrip() {
        McpToolBridge bridge = mock(McpToolBridge.class);
        ToolCallback t1 = mockTool("github_create_issue");
        ToolCallback t2 = mockTool("alt_1_filesystem_read");
        ToolCallback t3 = mockTool("slack_post");
        when(bridge.getAllToolCallbacks()).thenReturn(List.of(t1, t2, t3));
        McpProperties props = new McpProperties();
        props.setToolNamePrefixStrip(true);
        McpIntegrationImpl integration = new McpIntegrationImpl(bridge, props, mock(McpToolCallMapper.class));

        // 精确名 + 去前缀名（filesystem_read 命中 alt_1_filesystem_read）
        List<ToolCallback> picked = integration.getToolsForAgent(List.of("github_create_issue", "read"));

        assertEquals(2, picked.size());
    }

    @Test
    void getToolsForAgent_emptyAllowlist_returnsNothing() {
        McpToolBridge bridge = mock(McpToolBridge.class);
        McpIntegrationImpl integration =
                new McpIntegrationImpl(bridge, new McpProperties(), mock(McpToolCallMapper.class));

        assertTrue(integration.getToolsForAgent(List.of()).isEmpty());
        verify(bridge, never()).getAllToolCallbacks();
    }

    @Test
    void getToolUsageStats_mapsRows() {
        McpToolCallMapper mapper = mock(McpToolCallMapper.class);
        when(mapper.usageStats("github")).thenReturn(List.of(
                Map.of("tool_name", "create_issue", "cnt", 5L),
                Map.of("tool_name", "list_repos", "cnt", 2)
        ));
        McpIntegrationImpl integration =
                new McpIntegrationImpl(mock(McpToolBridge.class), new McpProperties(), mapper);

        Map<String, Long> stats = integration.getToolUsageStats("github");
        assertEquals(5L, stats.get("create_issue"));
        assertEquals(2L, stats.get("list_repos"));
    }
}
