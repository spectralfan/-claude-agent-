package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.coding.CodingVerifyTools;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.StackVerifyCommandDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import com.kama.jchatmind.coding.service.StackVerifyExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingVerifyToolsTest {

    private CodingTaskService taskService;
    private CodingWorkspaceService workspaceService;
    private SandboxCommandRunner sandboxCommandRunner;
    private StackVerifyExecutor stackVerifyExecutor;
    private CodingVerifyTools tools;
    private Path workspace;
    private CodingTask task;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createTempDirectory("coding-verify-tools-");
        taskService = mock(CodingTaskService.class);
        workspaceService = mock(CodingWorkspaceService.class);
        sandboxCommandRunner = mock(SandboxCommandRunner.class);
        stackVerifyExecutor = mock(StackVerifyExecutor.class);
        CodingProperties props = new CodingProperties();
        tools = new CodingVerifyTools(
                taskService,
                workspaceService,
                sandboxCommandRunner,
                mock(CodingVerificationService.class),
                props,
                stackVerifyExecutor);
        task = CodingTask.builder()
                .id("t1")
                .sessionId("s1")
                .workspaceRoot(workspace.toString())
                .workspacePath(".")
                .build();
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void verifyCodingFile_existingFile_returnsOk() throws Exception {
        Path file = workspace.resolve("js/game.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "console.log(1);");

        bindTask();
        String out = tools.verifyCodingFile("js/game.js");
        assertTrue(out.contains("exit code: 0"));
        assertTrue(out.contains("js/game.js"));
    }

    @Test
    void listStackVerifyCommands_delegatesToExecutor() {
        bindTask();
        when(stackVerifyExecutor.listVerifyCommands(task)).thenReturn("可用栈验证命令");
        String out = tools.listStackVerifyCommands();
        assertTrue(out.contains("可用栈验证命令"));
    }

    @Test
    void runStackVerify_delegatesToExecutor() {
        bindTask();
        when(stackVerifyExecutor.runByLabel(task, "npm test")).thenReturn("exit code: 0\nok");
        String out = tools.runStackVerify("npm test");
        assertTrue(out.contains("exit code: 0"));
    }

    @Test
    void runAllowedVerify_blockedCommand_returnsHint() {
        bindTask();
        when(stackVerifyExecutor.findByShellCommand(any(), anyString())).thenReturn(Optional.empty());
        String out = tools.runAllowedVerify("node -e \"bad\"");
        assertTrue(out.contains("list_stack_verify_commands"));
    }

    @Test
    void runAllowedVerify_stackMatch_delegatesToRunByLabel() {
        bindTask();
        StackVerifyCommandDTO cmd = StackVerifyCommandDTO.builder()
                .label("npm test")
                .type("shell")
                .command("npm test")
                .build();
        when(stackVerifyExecutor.findByShellCommand(task, "npm test")).thenReturn(Optional.of(cmd));
        when(stackVerifyExecutor.runByLabel(task, "npm test")).thenReturn("exit code: 0\nok");
        String out = tools.runAllowedVerify("npm test");
        assertTrue(out.contains("exit code: 0"));
    }

    @Test
    void checkJsSyntax_htmlPath_suggestsVerifyCodingFile() {
        bindTask();
        String out = tools.checkJsSyntax("index.html");
        assertTrue(out.contains("非法 JS 路径"));
        assertTrue(out.contains("verify_coding_file"));
    }

    @Test
    void checkJsSyntax_invalidPath_returnsError() {
        bindTask();
        String out = tools.checkJsSyntax("bad|path.js");
        assertTrue(out.contains("非法 JS 路径"));
    }

    private void bindTask() {
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        when(sandboxCommandRunner.run(anyList(), any(Path.class), anyInt(), anyInt()))
                .thenReturn(com.kama.jchatmind.coding.model.dto.CommandExecutionResult.builder()
                        .exitCode(0)
                        .output("syntax ok")
                        .build());
        doNothing().when(taskService).recordExecutionResult(anyString(), anyString(), anyString());
    }
}
