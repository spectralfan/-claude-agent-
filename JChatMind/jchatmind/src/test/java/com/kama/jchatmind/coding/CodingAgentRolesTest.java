package com.kama.jchatmind.coding;

import com.kama.jchatmind.model.dto.AgentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodingAgentRolesTest {

    @Test
    void isOrchestrator_detectsDelegateOnlyAgent() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("delegate_coding_task", "coding_subtask_tools"))
                .build();
        assertTrue(CodingAgentRoles.isOrchestrator(agent));
    }

    @Test
    void isOrchestrator_rejectsWorkerAgent() {
        AgentDTO agent = AgentDTO.builder()
                .allowedTools(List.of("coding_file_tools", "mark_coding_complete", "execute_command"))
                .build();
        assertFalse(CodingAgentRoles.isOrchestrator(agent));
    }
}
