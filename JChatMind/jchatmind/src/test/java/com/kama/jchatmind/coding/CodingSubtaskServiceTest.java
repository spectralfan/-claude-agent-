package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.coding.service.impl.CodingSubtaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodingSubtaskServiceTest {

    private CodingSubtaskServiceImpl service;
    private OrchestrationTaskService orchestrationTaskService;

    @BeforeEach
    void setUp() {
        orchestrationTaskService = mock(OrchestrationTaskService.class);
        service = new CodingSubtaskServiceImpl(orchestrationTaskService);
    }

    @Test
    void create_shouldDelegateToOrchestration() {
        when(orchestrationTaskService.create(
                any(), any(), eq(OrchestrationTaskRole.WORKER), any(), any(),
                any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(OrchestrationTaskDTO.builder()
                        .id("orch-1")
                        .parentSessionId("session-1")
                        .parentTaskId("task-1")
                        .role("WORKER")
                        .title("实现登录")
                        .goal("添加 JWT")
                        .status("READY")
                        .workerAgentId("worker-1")
                        .build());

        CodingSubtaskDTO created = service.create(
                "session-1", "task-1", "worker-1", "实现登录", "添加 JWT");
        assertNotNull(created.getId());
        assertEquals("WORKER", created.getRole());
        assertEquals("READY", created.getStatus());
    }

    @Test
    void markCompleted_shouldDelegate() {
        when(orchestrationTaskService.findById("orch-1"))
                .thenReturn(Optional.of(OrchestrationTaskDTO.builder()
                        .id("orch-1")
                        .status("COMPLETED")
                        .resultSummary("完成")
                        .build()));

        service.markCompleted("orch-1", "完成");
        verify(orchestrationTaskService).markCompleted("orch-1", "完成");
    }

    @Test
    void listByParentSession_shouldMapDtos() {
        when(orchestrationTaskService.listByParentSession("session-1"))
                .thenReturn(List.of(OrchestrationTaskDTO.builder()
                        .id("orch-1")
                        .role("REVIEWER")
                        .status("RUNNING")
                        .dependsOn(List.of("w-1"))
                        .build()));

        List<CodingSubtaskDTO> list = service.listByParentSession("session-1");
        assertEquals(1, list.size());
        assertEquals("REVIEWER", list.get(0).getRole());
        assertEquals(List.of("w-1"), list.get(0).getDependsOn());
    }
}
