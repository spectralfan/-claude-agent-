package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.coding.CodingFileTools;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodingFileToolsTest {

    private CodingTaskService taskService;
    private CodingWorkspaceService workspaceService;
    private CodingFileTools tools;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createTempDirectory("coding-file-tools-");
        taskService = mock(CodingTaskService.class);
        workspaceService = mock(CodingWorkspaceService.class);
        tools = new CodingFileTools(taskService, workspaceService,
                mock(com.kama.jchatmind.coding.registry.CodingChangeRegistry.class),
                mock(com.kama.jchatmind.realtime.ChatEventPublisher.class),
                mock(com.kama.jchatmind.coding.service.CodingVerificationService.class));
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void readFile_withoutContext_returnsError() {
        assertTrue(tools.readFile("pom.xml").contains("无 Coding 会话上下文"));
    }

    @Test
    void writeAndReadFile_withinWorkspace() throws Exception {
        CodingTask task = CodingTask.builder()
                .id("t1")
                .sessionId("s1")
                .workspaceRoot(workspace.toString())
                .workspacePath(".")
                .build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        when(workspaceService.isPathSafe(workspace, workspace.resolve("hello.txt"))).thenReturn(true);

        String writeResult = tools.writeFile("hello.txt", "hello world");
        assertTrue(writeResult.contains("成功写入"));

        String content = tools.readFile("hello.txt");
        assertEquals("hello world", content);
    }

    @Test
    void listDirectory_showsEntries() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "a");
        CodingTask task = CodingTask.builder()
                .id("t1")
                .sessionId("s1")
                .workspaceRoot(workspace.toString())
                .workspacePath(".")
                .build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        when(workspaceService.isPathSafe(workspace, workspace)).thenReturn(true);

        String listing = tools.listDirectory(".");
        assertTrue(listing.contains("a.txt"));
    }
}
