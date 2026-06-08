package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpShellArgumentEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        CodingSessionContext.clear();
    }

    @Test
    void enrich_shouldUnwrapNestedCommandJson() throws Exception {
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, mock(CodingTaskService.class), mock(CodingWorkspaceService.class),
                new McpProperties());

        String input = "{\"command\":\"{\\\"command\\\":\\\"dir\\\"}\"}";
        String out = enricher.enrich("execute_command", input);

        assertThat(objectMapper.readTree(out).get("command").asText()).isEqualTo("dir");
    }

    @Test
    void enrich_shouldInjectWorkingDirFromActiveTask() throws Exception {
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingWorkspaceService workspaceService = mock(CodingWorkspaceService.class);
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, taskService, workspaceService, new McpProperties());

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(Path.of("Z:/workspace/t1"));

        String out = enricher.enrich("run_terminal_cmd", "{\"command\":\"dir\"}");

        assertThat(objectMapper.readTree(out).get("workingDir").asText())
                .isEqualTo(Path.of("Z:/workspace/t1").toString());
    }

    @Test
    void enrich_shouldStripRedundantCdAndRewriteDir() throws Exception {
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingWorkspaceService workspaceService = mock(CodingWorkspaceService.class);
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, taskService, workspaceService, new McpProperties());

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(Path.of("Z:/workspace"));

        String input = "{\"command\":\"cd /d \\\"Z:\\\\workspace\\\" && dir \\\"Z:\\\\workspace\\\\js\\\\game.js\\\"\"}";
        String out = enricher.enrich("execute_command", input);

        JsonNode node = objectMapper.readTree(out);
        assertThat(node.get("workingDir").asText()).isEqualTo(Path.of("Z:/workspace").toString());
        assertThat(node.get("command").asText()).isEqualTo("dir js\\game.js");
    }

    @Test
    void enrich_shouldRelativizeNodeCheckAbsolutePath() throws Exception {
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingWorkspaceService workspaceService = mock(CodingWorkspaceService.class);
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, taskService, workspaceService, new McpProperties());

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(Path.of("Z:/workspace"));

        String input = "{\"command\":\"node --check \\\"Z:\\\\workspace\\\\js\\\\game.js\\\"\",\"workingDir\":\"Z:\\\\workspace\"}";
        String out = enricher.enrich("execute_command", input);

        assertThat(objectMapper.readTree(out).get("command").asText())
                .isEqualTo("node --check js\\game.js");
    }

    @Test
    void enrich_nodeCheckHtml_shouldRewriteToDir() throws Exception {
        CodingTaskService taskService = mock(CodingTaskService.class);
        CodingWorkspaceService workspaceService = mock(CodingWorkspaceService.class);
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, taskService, workspaceService, new McpProperties());

        CodingTask task = CodingTask.builder().id("t1").sessionId("s1").build();
        CodingSessionContext.set("s1", "a1");
        when(taskService.getActiveTask("s1")).thenReturn(task);
        when(workspaceService.resolveForTask(task)).thenReturn(Path.of("Z:/workspace"));

        String input = "{\"command\":\"node --check \\\"Z:\\\\workspace\\\\tank-battle.html\\\"\",\"workingDir\":\"Z:\\\\workspace\"}";
        String out = enricher.enrich("execute_command", input);

        assertThat(objectMapper.readTree(out).get("command").asText())
                .isEqualTo("dir tank-battle.html");
    }

    @Test
    void enrich_nonShellTool_shouldPassThrough() {
        McpShellArgumentEnricher enricher = new McpShellArgumentEnricher(
                objectMapper, mock(CodingTaskService.class), mock(CodingWorkspaceService.class),
                new McpProperties());

        assertThat(enricher.enrich("read_file", "{\"path\":\"a.txt\"}"))
                .isEqualTo("{\"path\":\"a.txt\"}");
    }
}
