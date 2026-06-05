package com.kama.jchatmind.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.tools.coding.CodingRunTool;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingApprovalMode;
import com.kama.jchatmind.coding.service.CodingApprovalPolicy;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CodingRunToolTest {

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void strictMode_testGoal_shouldEnterWaitingApproval() {
        CodingProperties props = new CodingProperties();
        props.getApproval().setEnabled(true);
        props.getApproval().setDefaultMode(CodingApprovalMode.STRICT);
        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").workspacePath(".").build();
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingCommandService commandService = mock(CodingCommandService.class);
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(taskService.getTaskEntity("t1")).thenReturn(task);
        CodingRunTool tool = new CodingRunTool(taskService, commandService,
                new CodingApprovalPolicy(props), mock(RealtimeNotifier.class), new ObjectMapper());
        CodingSessionContext.set("s1", "a1");

        String result = tool.runMavenCommand("test", null);
        assertTrue(result.contains("需要审批"));
        verify(taskService).markWaitingApproval(eq("t1"), eq("mvn test"), any(), any());
        verifyNoInteractions(commandService);
    }

    @Test
    void developmentMode_testGoal_shouldExecuteImmediately() {
        CodingProperties props = new CodingProperties();
        props.getApproval().setEnabled(true);
        props.getApproval().setDefaultMode(CodingApprovalMode.DEVELOPMENT);
        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").workspacePath(".").build();
        CodingRunTool tool = buildTool(props, task);
        CodingSessionContext.set("s1", "a1");

        String result = tool.runMavenCommand("test", null);
        assertTrue(result.contains("ok"));
    }

    @Test
    void compileGoal_shouldExecuteImmediately() {
        CodingProperties props = new CodingProperties();
        props.getApproval().setEnabled(true);
        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").workspacePath(".").build();
        CodingRunTool tool = buildTool(props, task);
        CodingSessionContext.set("s1", "a1");

        String result = tool.runMavenCommand("compile", null);
        assertTrue(result.contains("ok"));
    }

    private CodingRunTool buildTool(CodingProperties props, CodingTask task) {
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingCommandService commandService = mock(CodingCommandService.class);
        RealtimeNotifier realtimeNotifier = mock(RealtimeNotifier.class);
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(taskService.getTaskEntity("t1")).thenReturn(task);
        when(commandService.executeMaven(any())).thenReturn(CommandExecutionResult.builder()
                .exitCode(0).timeout(false).output("ok").build());
        return new CodingRunTool(taskService, commandService,
                new CodingApprovalPolicy(props), realtimeNotifier, new ObjectMapper());
    }
}
