package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.model.dto.AgentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingAgentRolesTest {

    @Test
    void isScheduler_detectsOrchestrationTools() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("orchestration_task_tools", "orchestration_read_tools"))
                .build();
        assertTrue(CodingAgentRoles.isScheduler(agent));
        assertEquals(CodingAgentRoles.AgentRole.SCHEDULER, CodingAgentRoles.resolveRole(agent));
    }

    @Test
    void isOrchestrator_detectsDelegateOnlyAgent() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("delegate_coding_task", "coding_subtask_tools"))
                .build();
        assertTrue(CodingAgentRoles.isOrchestrator(agent));
    }

    @Test
    void isReviewer_detectsReadOnlyAgent() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("orchestration_read_tools"))
                .build();
        assertTrue(CodingAgentRoles.isReviewer(agent));
        assertEquals(CodingAgentRoles.AgentRole.REVIEWER, CodingAgentRoles.resolveRole(agent));
    }

    @Test
    void isWorker_rejectsOrchestratorAgent() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("coding_file_tools", "mark_coding_complete", "execute_command"))
                .build();
        assertFalse(CodingAgentRoles.isOrchestrator(agent));
        assertEquals(CodingAgentRoles.AgentRole.WORKER, CodingAgentRoles.resolveRole(agent));
    }

    @Test
    void filterToolsByRole_blocksOrchestrationForWorker() {
        List<Tool> tools = List.of(
                tool("coding_file_tools"),
                tool("orchestration_task_tools")
        );
        List<Tool> filtered = CodingAgentRoles.filterToolsByRole(tools, CodingAgentRoles.AgentRole.WORKER);
        assertEquals(1, filtered.size());
        assertEquals("coding_file_tools", filtered.get(0).getName());
    }

    private static Tool tool(String name) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return name;
            }

            @Override
            public ToolType getType() {
                return ToolType.OPTIONAL;
            }
        };
    }
}
