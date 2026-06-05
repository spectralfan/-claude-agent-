package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.model.dto.CreateCodingTaskRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.WorkspaceDetectService;
import com.kama.jchatmind.coding.service.WorkspaceScaffoldService;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "realtime.messaging.mode=local",
        "coding.agent-preset.enabled=false",
        "coding.workspace.root=${java.io.tmpdir}/jchatmind-mvp-it",
        "coding.workspace.allowed-roots[0].name=MvpIT",
        "coding.workspace.allowed-roots[0].path=${java.io.tmpdir}/jchatmind-mvp-it"
})
class CodingMvpIntegrationTest {

    @Autowired
    private CodingStackService codingStackService;
    @Autowired
    private WorkspaceDetectService workspaceDetectService;
    @Autowired
    private WorkspaceScaffoldService workspaceScaffoldService;
    @Autowired
    private CodingTaskService codingTaskService;
    @Autowired
    private ChatEventPublisher chatEventPublisher;

    @Test
    void stacks_shouldIncludePythonPytest() {
        assertTrue(codingStackService.findById("python-pytest").isPresent());
    }

    @Test
    void emptyWorkspace_scaffold_pythonPytest() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "jchatmind-mvp-it");
        Files.createDirectories(root);
        String sub = "py-mvp-" + System.nanoTime();
        Path projectDir = root.resolve(sub);
        Files.createDirectories(projectDir);

        var detect = workspaceDetectService.detect(root.toString(), sub);
        assertTrue(detect.isEmptyWorkspace());
        assertNull(detect.getStackId());

        String sessionId = "mvp-session-" + System.nanoTime();
        CreateCodingTaskRequest request = new CreateCodingTaskRequest();
        request.setSessionId(sessionId);
        request.setAgentId("00000000-0000-0000-0000-000000000001");
        request.setWorkspaceRoot(root.toString());
        request.setWorkspacePath(sub);
        request.setStackId("python-pytest");
        request.setScaffoldOnCreate(true);

        String taskId = codingTaskService.createTask(request);
        CodingTask task = codingTaskService.getTaskEntity(taskId);

        assertTrue(Files.exists(projectDir.resolve("pyproject.toml")));
        assertTrue(Files.exists(projectDir.resolve("tests/test_todo.py")));

        var redetect = workspaceDetectService.detect(root.toString(), sub);
        assertEquals("python-pytest", redetect.getStackId());
        assertEquals("pyproject.toml", redetect.getMatchedFile());
    }

    @Test
    void chatEventPublisher_shouldBeAvailable() {
        assertNotNull(chatEventPublisher);
    }
}
