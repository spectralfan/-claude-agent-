package com.kama.jchatmind.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.tools.coding.CodingRunTool;
import com.kama.jchatmind.coding.controller.CodingController;
import com.kama.jchatmind.coding.model.dto.CodingFileContentDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskDTO;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.FileNode;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingApprovalService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodingController.class)
class CodingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CodingTaskService codingTaskService;

    @MockitoBean
    private CodingApprovalService codingApprovalService;

    @MockitoBean
    private CodingRunTool codingRunTool;

    @MockitoBean
    private RealtimeNotifier realtimeNotifier;

    @MockitoBean
    private com.kama.jchatmind.coding.service.CodingWorkspaceService codingWorkspaceService;

    @MockitoBean
    private com.kama.jchatmind.coding.service.CodingTaskSummaryService codingTaskSummaryService;

    @MockitoBean
    private com.kama.jchatmind.coding.service.CodingCommandService codingCommandService;

    @Test
    void createTask_shouldReturnTaskDto() throws Exception {
        when(codingTaskService.createTask(any())).thenReturn("task-1");
        when(codingTaskService.getTask("task-1")).thenReturn(CodingTaskDTO.builder()
                .id("task-1").sessionId("s1").agentId("a1")
                .status("PENDING").workspaceRoot("D:/proj/app").workspacePath(".").build());

        String body = objectMapper.writeValueAsString(Map.of(
                "sessionId", "s1",
                "agentId", "a1",
                "workspaceRoot", "D:/proj/app",
                "workspacePath", "."));

        mockMvc.perform(post("/api/coding/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("task-1"))
                .andExpect(jsonPath("$.data.workspaceRoot").value("D:/proj/app"));

        verify(codingTaskService).createTask(any());
        verify(realtimeNotifier).tryPublish(eq("s1"), any());
    }

    @Test
    void runMaven_shouldReturnToolOutput() throws Exception {
        when(codingRunTool.runMavenForTask(eq("task-1"), eq("compile"),
                nullable(String.class), eq("s1"), eq("a1")))
                .thenReturn("BUILD SUCCESS");

        String body = objectMapper.writeValueAsString(Map.of(
                "goal", "compile", "sessionId", "s1", "agentId", "a1"));

        mockMvc.perform(post("/api/coding/tasks/task-1/run-maven")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("BUILD SUCCESS"));
    }

    @Test
    void approve_shouldReturnExecutionResult() throws Exception {
        when(codingApprovalService.approve("task-1")).thenReturn(
                CommandExecutionResult.builder()
                        .exitCode(0).output("BUILD SUCCESS").timeout(false).build());
        when(codingTaskService.getTaskEntity("task-1")).thenReturn(
                CodingTask.builder().id("task-1").sessionId("s1")
                        .command("mvn test").build());

        mockMvc.perform(post("/api/coding/tasks/task-1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.exitCode").value(0));

        verify(codingApprovalService).approve("task-1");
    }

    @Test
    void reject_shouldInvokeServiceAndReturnOk() throws Exception {
        when(codingTaskService.getTaskEntity("task-1")).thenReturn(
                CodingTask.builder().id("task-1").sessionId("s1").build());

        String body = objectMapper.writeValueAsString(Map.of("reason", "不安全"));

        mockMvc.perform(post("/api/coding/tasks/task-1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(codingApprovalService).reject("task-1", "不安全");
    }

    @Test
    void getActiveTask_shouldReturnTaskWhenPresent() throws Exception {
        when(codingTaskService.getActiveTask("s1")).thenReturn(
                CodingTask.builder().id("task-1").sessionId("s1").agentId("a1")
                        .status("running").workspacePath(".").build());
        when(codingTaskService.getTask("task-1")).thenReturn(CodingTaskDTO.builder()
                .id("task-1").sessionId("s1").agentId("a1")
                .status("RUNNING").workspacePath(".").build());

        mockMvc.perform(get("/api/coding/tasks/session/s1/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("task-1"));
    }

    @Test
    void getActiveTask_shouldReturnNullWhenMissing() throws Exception {
        when(codingTaskService.getActiveTask("s-empty")).thenReturn(null);

        mockMvc.perform(get("/api/coding/tasks/session/s-empty/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getTaskTree_shouldReturnFileNodes() throws Exception {
        when(codingTaskService.getTaskEntity("task-1")).thenReturn(
                CodingTask.builder().id("task-1").workspaceRoot("D:/proj").workspacePath(".").build());
        when(codingWorkspaceService.listDirectoryForTask(any(), eq("."))).thenReturn(List.of(
                FileNode.builder().name("pom.xml").relativePath("pom.xml").directory(false).build()
        ));

        mockMvc.perform(get("/api/coding/tasks/task-1/tree").param("path", "."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("pom.xml"));
    }

    @Test
    void getTaskFile_shouldReturnContent() throws Exception {
        when(codingTaskService.getTaskEntity("task-1")).thenReturn(
                CodingTask.builder().id("task-1").workspaceRoot("D:/proj").workspacePath(".").build());
        when(codingWorkspaceService.readFileForTask(any(), eq("pom.xml"))).thenReturn(
                CodingFileContentDTO.builder()
                        .relativePath("pom.xml")
                        .content("<project/>")
                        .size(10)
                        .truncated(false)
                        .language("xml")
                        .build());

        mockMvc.perform(get("/api/coding/tasks/task-1/file").param("path", "pom.xml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("<project/>"));
    }

    @Test
    void getTask_shouldReturnTaskDto() throws Exception {
        when(codingTaskService.getTask("task-1")).thenReturn(CodingTaskDTO.builder()
                .id("task-1").sessionId("s1").agentId("a1")
                .status("WAITING_APPROVAL").workspaceRoot("D:/proj").workspacePath(".")
                .command("mvn test").build());

        mockMvc.perform(get("/api/coding/tasks/task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceRoot").value("D:/proj"));
    }
}
