package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.coding.CodingSearchTools;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.registry.CodingChangeRegistry;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodingSearchToolsTest {

    private CodingTaskService taskService;
    private CodingWorkspaceService workspaceService;
    private CodingVerificationService verificationService;
    private CodingSearchTools tools;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createTempDirectory("coding-search-");
        taskService = mock(CodingTaskService.class);
        workspaceService = mock(CodingWorkspaceService.class);
        verificationService = mock(CodingVerificationService.class);
        tools = new CodingSearchTools(
                taskService,
                workspaceService,
                mock(CodingChangeRegistry.class),
                verificationService,
                mock(RealtimeNotifier.class),
                new CodingProperties()
        );
    }

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void searchCodingFiles_findsMatch() throws Exception {
        Files.writeString(workspace.resolve("App.java"), "class App { void hello() {} }");
        CodingTask task = task("t1", "s1");
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        when(workspaceService.isPathSafe(eq(workspace), any())).thenReturn(true);

        String result = tools.searchCodingFiles("hello", "*.java");
        assertTrue(result.contains("App.java"));
    }

    @Test
    void applyCodingPatch_shouldInvalidateVerification() throws Exception {
        Path file = workspace.resolve("a.txt");
        Files.writeString(file, "old line\n");
        CodingTask task = task("t1", "s1");
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(workspace);
        when(workspaceService.isPathSafe(workspace, file)).thenReturn(true);

        String result = tools.applyCodingPatch("a.txt", "old line", "new line");
        assertTrue(result.contains("成功 patch"));
        verify(verificationService).invalidate("t1");
        assertEquals("new line\n", Files.readString(file));
    }

    private static CodingTask task(String id, String sessionId) {
        return CodingTask.builder().id(id).sessionId(sessionId).build();
    }
}
