package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.coding.DelegateCodingTaskTool;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingSubtaskExecutor;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.mapper.AgentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DelegateCodingTaskToolTest {

    private DelegateCodingTaskTool tool;
    private CodingSubtaskExecutor executor;
    private CodingSubtaskService subtaskService;

    @BeforeEach
    void setUp() {
        CodingSubagentProperties props = new CodingSubagentProperties();
        props.setEnabled(true);
        subtaskService = mock(CodingSubtaskService.class);
        executor = mock(CodingSubtaskExecutor.class);
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingAgentPresetService presetService = mock(CodingAgentPresetService.class);
        AgentMapper agentMapper = mock(AgentMapper.class);

        tool = new DelegateCodingTaskTool(
                props, subtaskService, executor, taskService, presetService, agentMapper);

        CodingTask task = mock(CodingTask.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getSessionId()).thenReturn("session-1");
        when(taskService.getActiveTask("session-1")).thenReturn(task);
        when(presetService.findPreset()).thenReturn(Optional.of(
                CodingAgentPresetDTO.builder().agentId("worker-agent-id").build()));
        when(subtaskService.create(any(), any(), any(), any(), any()))
                .thenReturn(CodingSubtaskDTO.builder()
                        .id("sub-1")
                        .title("子任务")
                        .status("PENDING")
                        .build());

        CodingSessionContext.set("session-1", "orch-agent");
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void delegate_shouldStartAsyncSubtask() {
        String result = tool.delegateCodingTask("实现用户 API", "用户模块");
        assertTrue(result.contains("subTaskId=sub-1"));
        assertTrue(result.contains("RUNNING"));
        verify(executor).execute(any(CodingSubtaskDTO.class));
    }

    @Test
    void delegate_withoutSession_shouldFail() {
        CodingSessionContext.clear();
        String result = tool.delegateCodingTask("goal", "title");
        assertTrue(result.startsWith("错误"));
        verify(executor, never()).execute(any());
    }
}
