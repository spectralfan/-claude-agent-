package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.coding.DelegateCodingTaskTool;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.mapper.AgentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DelegateCodingTaskToolTest {

    private DelegateCodingTaskTool tool;
    private OrchestrationTaskService orchestrationTaskService;

    @BeforeEach
    void setUp() {
        CodingSubagentProperties props = new CodingSubagentProperties();
        props.setEnabled(true);
        orchestrationTaskService = mock(OrchestrationTaskService.class);
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingAgentPresetService presetService = mock(CodingAgentPresetService.class);
        AgentMapper agentMapper = mock(AgentMapper.class);

        tool = new DelegateCodingTaskTool(
                props, orchestrationTaskService, taskService, presetService, agentMapper);

        CodingTask task = mock(CodingTask.class);
        when(task.getId()).thenReturn("task-1");
        when(taskService.getActiveTask("session-1")).thenReturn(task);
        when(presetService.findPreset()).thenReturn(Optional.of(
                CodingAgentPresetDTO.builder().agentId("worker-agent-id").build()));
        when(orchestrationTaskService.create(
                any(), any(), eq(OrchestrationTaskRole.WORKER), any(), any(),
                any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(OrchestrationTaskDTO.builder()
                        .id("sub-1")
                        .title("子任务")
                        .status("READY")
                        .build());

        CodingSessionContext.set("session-1", "orch-agent");
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void delegate_shouldCreateOrchestrationTask() {
        String result = tool.delegateCodingTask("实现用户 API", "用户模块");
        assertTrue(result.contains("taskId=sub-1"));
        assertTrue(result.contains("READY"));
        verify(orchestrationTaskService).create(
                eq("session-1"), eq("task-1"), eq(OrchestrationTaskRole.WORKER),
                any(), eq("实现用户 API"), isNull(), any(), any(),
                eq("worker-agent-id"), eq(1), isNull(), isNull());
    }

    @Test
    void delegate_withoutSession_shouldFail() {
        CodingSessionContext.clear();
        String result = tool.delegateCodingTask("goal", "title");
        assertTrue(result.startsWith("错误"));
        verify(orchestrationTaskService, never()).create(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }
}
