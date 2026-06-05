package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.WorkspaceDetectResultDTO;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.impl.WorkspaceDetectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceDetectServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void detect_emptyWorkspace_shouldMarkEmpty() throws Exception {
        Path empty = tempDir.resolve("empty-proj");
        Files.createDirectories(empty);
        WorkspaceDetectServiceImpl service = buildService(tempDir);
        WorkspaceDetectResultDTO result = service.detect(tempDir.toString(), "empty-proj");
        assertTrue(result.isEmptyWorkspace());
        assertNull(result.getStackId());
    }

    @Test
    void detect_pomXml_shouldMatchJavaMaven() throws Exception {
        Path javaProj = tempDir.resolve("java-proj");
        Files.createDirectories(javaProj);
        Files.writeString(javaProj.resolve("pom.xml"), "<project/>");
        WorkspaceDetectServiceImpl service = buildService(tempDir);
        WorkspaceDetectResultDTO result = service.detect(tempDir.toString(), "java-proj");
        assertEquals("java-maven", result.getStackId());
        assertEquals("pom.xml", result.getMatchedFile());
    }

    private WorkspaceDetectServiceImpl buildService(Path root) {
        CodingWorkspaceService workspaceService = mock(CodingWorkspaceService.class);
        when(workspaceService.resolveAllowedRoot(any())).thenReturn(root);
        when(workspaceService.isPathSafe(any(), any())).thenReturn(true);

        CodingStackService stackService = mock(CodingStackService.class);
        CodingStackDTO java = new CodingStackDTO();
        java.setId("java-maven");
        java.setDisplayName("Java Maven");
        java.setLanguage("java");
        java.setDetectFiles(List.of("pom.xml"));
        when(stackService.listStacks()).thenReturn(List.of(java));

        return new WorkspaceDetectServiceImpl(workspaceService, stackService);
    }
}
