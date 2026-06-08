package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.bridge.CodingMcpOutputBridge;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CodingMcpOutputBridgeTest {

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void onToolResult_terminalTool_shouldPushSse() {
        CodingTaskService taskService = mock(CodingTaskService.class);
        ChatEventPublisher publisher = mock(ChatEventPublisher.class);
        CodingVerificationService verificationService = mock(CodingVerificationService.class);
        CodingProperties props = new CodingProperties();
        CodingMcpOutputBridge bridge = new CodingMcpOutputBridge(
                taskService, publisher, props, verificationService);

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);

        bridge.onToolResult("run_terminal_cmd", "{\"command\":\"pytest\"}", "exit code: 0\n3 passed");

        verify(publisher).publish(eq("s1"), argThat(msg ->
                msg.getType() == SseMessage.Type.CODING_COMMAND_OUTPUT
                        && "t1".equals(msg.getPayload().getTaskId())));
        verify(taskService).recordExecutionResult(eq("t1"), any(), any());
        verify(verificationService).recordSuccess(eq("t1"), any(), eq(0));
    }

    @Test
    void onToolResult_nonTerminalTool_shouldSkip() {
        CodingTaskService taskService = mock(CodingTaskService.class);
        ChatEventPublisher publisher = mock(ChatEventPublisher.class);
        CodingMcpOutputBridge bridge = new CodingMcpOutputBridge(
                taskService, publisher, new CodingProperties(),
                mock(CodingVerificationService.class));
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(
                CodingTask.builder().id("t1").sessionId("s1").build());

        bridge.onToolResult("read_file", "{}", "content");

        verifyNoInteractions(publisher);
    }

    @Test
    void onToolResult_exitCode1_shouldNotRecordSuccess() {
        CodingTaskService taskService = mock(CodingTaskService.class);
        ChatEventPublisher publisher = mock(ChatEventPublisher.class);
        CodingVerificationService verificationService = mock(CodingVerificationService.class);
        CodingMcpOutputBridge bridge = new CodingMcpOutputBridge(
                taskService, publisher, new CodingProperties(), verificationService);

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);

        bridge.onToolResult("execute_command", "{\"command\":\"bad\"}",
                "stderr:\nnot found\nexit code: 1");

        verify(publisher).publish(eq("s1"), argThat(msg ->
                msg.getPayload().getExitCode() == 1 && Boolean.FALSE.equals(msg.getPayload().getDone())));
        verify(verificationService, never()).recordSuccess(any(), any(), anyInt());
    }
}
