package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.StackVerifyCommandDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.impl.StackVerifyExecutorImpl;
import com.kama.jchatmind.mcp.bridge.McpShellExecutor;
import com.kama.jchatmind.mcp.bridge.McpShellResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StackVerifyExecutorTest {

    private CodingStackService stackService;
    private CodingWorkspaceService workspaceService;
    private CodingCommandService commandService;
    private CodingTaskService taskService;
    private CodingVerificationService verificationService;
    private McpShellExecutor mcpShellExecutor;
    private StackVerifyExecutorImpl executor;
    private CodingTask task;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createTempDirectory("stack-verify-");
        stackService = mock(CodingStackService.class);
        workspaceService = mock(CodingWorkspaceService.class);
        commandService = mock(CodingCommandService.class);
        taskService = mock(CodingTaskService.class);
        verificationService = mock(CodingVerificationService.class);
        mcpShellExecutor = mock(McpShellExecutor.class);

        CodingProperties props = new CodingProperties();
        props.getVerify().setBackend("auto");
        props.getVerify().setFallbackEnabled(true);

        executor = new StackVerifyExecutorImpl(
                stackService,
                workspaceService,
                commandService,
                taskService,
                verificationService,
                mcpShellExecutor,
                props);

        task = CodingTask.builder()
                .id("t1")
                .sessionId("s1")
                .workspaceRoot(workspace.toString())
                .workspacePath(".")
                .metadata(CodingTaskMetadata.builder().stackId("node-npm").build().toJson())
                .build();

        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        doNothing().when(taskService).recordExecutionResult(anyString(), anyString(), anyString());
        doNothing().when(verificationService).recordSuccess(anyString(), anyString(), anyInt());

        CodingStackDTO stack = new CodingStackDTO();
        stack.setId("node-npm");
        stack.setVerifyCommands(List.of(
                StackVerifyCommandDTO.builder()
                        .label("npm test")
                        .type("shell")
                        .command("npm test")
                        .build()
        ));
        when(stackService.findById("node-npm")).thenReturn(Optional.of(stack));
    }

    @Test
    void listVerifyCommands_returnsConfiguredLabels() {
        String out = executor.listVerifyCommands(task);
        assertTrue(out.contains("npm test"));
        assertTrue(out.contains("run_stack_verify"));
    }

    @Test
    void runByLabel_mcpSuccess_recordsVerification() {
        when(mcpShellExecutor.execute(eq("npm test"), anyString()))
                .thenReturn(Optional.of(new McpShellResult(0, "exit code: 0\nok")));

        String out = executor.runByLabel(task, "npm test");

        assertTrue(out.contains("exit code: 0"));
        verify(verificationService).recordSuccess(eq("t1"), eq("npm test"), eq(0));
        verify(commandService, never()).executeShell(anyString(), anyString());
    }

    @Test
    void runByLabel_mcpUnavailable_fallsBackToSandbox() {
        when(mcpShellExecutor.execute(eq("npm test"), anyString())).thenReturn(Optional.empty());
        when(commandService.executeShell(eq("t1"), eq("npm test")))
                .thenReturn(CommandExecutionResult.builder().exitCode(0).output("tests ok").build());

        String out = executor.runByLabel(task, "npm test");

        assertTrue(out.contains("exit code: 0"));
        verify(commandService).executeShell("t1", "npm test");
    }

    @Test
    void runByLabel_fileType_checksExistence() throws Exception {
        Path index = workspace.resolve("index.html");
        Files.writeString(index, "<html></html>");

        CodingStackDTO stack = new CodingStackDTO();
        stack.setId("static-html");
        stack.setVerifyCommands(List.of(
                StackVerifyCommandDTO.builder()
                        .label("确认 index.html")
                        .type("file")
                        .path("index.html")
                        .build()
        ));
        when(stackService.findById("node-npm")).thenReturn(Optional.of(stack));

        String out = executor.runByLabel(task, "确认 index.html");

        assertTrue(out.contains("exit code: 0"));
        assertTrue(out.contains("index.html"));
        verify(verificationService).recordSuccess(eq("t1"), eq("verify_file:index.html"), eq(0));
    }

    @Test
    void runByLabel_unknownLabel_returnsError() {
        String out = executor.runByLabel(task, "missing");
        assertTrue(out.contains("未找到验证命令"));
    }
}
