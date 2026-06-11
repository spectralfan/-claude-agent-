package com.kama.jchatmind.agent.tools.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SchedulerRunContext;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingReviewerPresetService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrchestrationTaskToolsTest {

    private OrchestrationTaskService orchestrationTaskService;
    private CodingTaskService codingTaskService;
    private OrchestrationTaskTools tools;

    @BeforeEach
    void setUp() {
        orchestrationTaskService = mock(OrchestrationTaskService.class);
        codingTaskService = mock(CodingTaskService.class);
        tools = new OrchestrationTaskTools(
                orchestrationTaskService,
                codingTaskService,
                mock(CodingAgentPresetService.class),
                mock(CodingReviewerPresetService.class),
                new ObjectMapper());
        SchedulerRunContext.begin();
        CodingSessionContext.set("parent-session", "scheduler-agent");
        CodingTask task = CodingTask.builder().id("ct1").sessionId("parent-session").build();
        when(codingTaskService.getActiveTask("parent-session")).thenReturn(task);
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
        SchedulerRunContext.clear();
    }

    @Test
    void listOrchestrationTasks_activeTasks_firstList_includesIdleDirective() {
        OrchestrationTaskDTO running = OrchestrationTaskDTO.builder()
                .id("t1")
                .status("RUNNING")
                .role("WORKER")
                .title("w1")
                .goal("do work")
                .build();
        when(orchestrationTaskService.listByParentSession("parent-session")).thenReturn(List.of(running));

        String out = tools.listOrchestrationTasks();

        assertTrue(out.contains("RUNNING"));
        assertTrue(out.contains(SchedulerRunContext.IDLE_DIRECTIVE_PREFIX));
    }

    @Test
    void listOrchestrationTasks_activeTasks_secondList_blocksRepeatPoll() {
        OrchestrationTaskDTO running = OrchestrationTaskDTO.builder()
                .id("t1")
                .status("RUNNING")
                .role("WORKER")
                .title("w1")
                .goal("do work")
                .build();
        when(orchestrationTaskService.listByParentSession("parent-session")).thenReturn(List.of(running));

        tools.listOrchestrationTasks();
        String second = tools.listOrchestrationTasks();

        assertTrue(second.contains("禁止重复"));
        assertTrue(second.contains(SchedulerRunContext.IDLE_DIRECTIVE_PREFIX));
    }
}
