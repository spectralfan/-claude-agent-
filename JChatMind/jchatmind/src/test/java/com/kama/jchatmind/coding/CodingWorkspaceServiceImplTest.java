package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.impl.CodingWorkspaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodingWorkspaceServiceImplTest {

    private Path defaultRoot;
    private Path projectRoot;
    private CodingWorkspaceServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        defaultRoot = Files.createTempDirectory("coding-default-");
        projectRoot = Files.createTempDirectory("coding-project-");
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");

        CodingProperties props = new CodingProperties();
        props.getWorkspace().setRoot(defaultRoot.toString());
        CodingProperties.AllowedRoot allowed = new CodingProperties.AllowedRoot();
        allowed.setName("测试工程");
        allowed.setPath(projectRoot.toString());
        props.getWorkspace().setAllowedRoots(java.util.List.of(allowed));

        service = new CodingWorkspaceServiceImpl(props);
        ReflectionTestUtils.invokeMethod(service, "initRoots");
    }

    @Test
    void resolveSafePath_shouldRejectEscapePath() {
        assertThrows(IllegalArgumentException.class, () -> service.resolveSafePath("../outside"));
    }

    @Test
    void resolveAllowedRoot_shouldAcceptWhitelistedProject() {
        Path resolved = service.resolveAllowedRoot(projectRoot.toString());
        assertTrue(resolved.toString().contains("coding-project"));
    }

    @Test
    void resolveAllowedRoot_shouldRejectUnknownPath() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveAllowedRoot("C:/not-in-whitelist"));
    }

    @Test
    void resolveForTask_shouldUseTaskRootAndSubPath() {
        CodingTask task = CodingTask.builder()
                .workspaceRoot(projectRoot.toString())
                .workspacePath(".")
                .build();
        Path exec = service.resolveForTask(task);
        assertTrue(Files.exists(exec.resolve("pom.xml")));
    }

    @Test
    void listDirectoryForTask_shouldListChildren() throws Exception {
        Files.createDirectory(projectRoot.resolve("src"));
        CodingTask task = CodingTask.builder()
                .workspaceRoot(projectRoot.toString())
                .workspacePath(".")
                .build();
        var nodes = service.listDirectoryForTask(task, ".");
        assertTrue(nodes.stream().anyMatch(n -> "pom.xml".equals(n.getName())));
        assertTrue(nodes.stream().anyMatch(n -> "src".equals(n.getName()) && n.isDirectory()));
    }

    @Test
    void listDirectoryTreeForTask_shouldWalkNestedDirs() throws Exception {
        Path src = projectRoot.resolve("src/main");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "class App {}");
        CodingTask task = CodingTask.builder()
                .workspaceRoot(projectRoot.toString())
                .workspacePath(".")
                .build();
        String tree = service.listDirectoryTreeForTask(task, ".", 3);
        assertTrue(tree.contains("[D] src"));
        assertTrue(tree.contains("App.java"));
    }

    @Test
    void readFileForTask_shouldReturnContent() throws Exception {
        CodingTask task = CodingTask.builder()
                .workspaceRoot(projectRoot.toString())
                .workspacePath(".")
                .build();
        var file = service.readFileForTask(task, "pom.xml");
        assertEquals("pom.xml", file.getRelativePath());
        assertTrue(file.getContent().contains("project"));
    }
}
