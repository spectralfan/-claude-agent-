package com.kama.jchatmind.coding;

import com.kama.jchatmind.model.dto.AgentDTO;

import java.util.List;

/**
 * 区分 Coding Orchestrator（只委派）与 Worker（执行开发）。
 */
public final class CodingAgentRoles {

    private static final String DELEGATE_TOOL = "delegate_coding_task";

    private CodingAgentRoles() {
    }

    public static boolean isOrchestrator(AgentDTO agent) {
        if (agent == null) {
            return false;
        }
        List<String> tools = agent.getAllowedTools();
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        return tools.contains(DELEGATE_TOOL)
                && !tools.contains("coding_file_tools")
                && !tools.contains("mark_coding_complete");
    }
}
