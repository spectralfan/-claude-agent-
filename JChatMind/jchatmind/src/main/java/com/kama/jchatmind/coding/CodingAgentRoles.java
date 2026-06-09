package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.model.dto.AgentDTO;

import java.util.List;
import java.util.Set;

/**
 * 区分 Scheduler（DAG 编排）、Worker（执行开发）、Reviewer（只读审查）。
 */
public final class CodingAgentRoles {

    private static final String DELEGATE_TOOL = "delegate_coding_task";
    private static final String ORCHESTRATION_TASK_TOOLS = "orchestration_task_tools";
    private static final String ORCHESTRATION_READ_TOOLS = "orchestration_read_tools";
    private static final String ORCHESTRATION_SHELL_TOOLS = "orchestration_shell_tools";
    private static final String CODING_FILE_TOOLS = "coding_file_tools";

    public enum AgentRole {
        SCHEDULER,
        WORKER,
        REVIEWER,
        UNKNOWN
    }

    private CodingAgentRoles() {
    }

    public static AgentRole resolveRole(AgentDTO agent) {
        if (isScheduler(agent)) {
            return AgentRole.SCHEDULER;
        }
        if (isReviewer(agent)) {
            return AgentRole.REVIEWER;
        }
        if (isWorker(agent)) {
            return AgentRole.WORKER;
        }
        return AgentRole.UNKNOWN;
    }

    public static boolean isScheduler(AgentDTO agent) {
        return hasTool(agent, ORCHESTRATION_TASK_TOOLS);
    }

    /** 兼容旧 Orchestrator 预设（delegate_coding_task + 无写文件工具） */
    public static boolean isOrchestrator(AgentDTO agent) {
        if (agent == null) {
            return false;
        }
        if (isScheduler(agent)) {
            return true;
        }
        List<String> tools = agent.getAllowedTools();
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        return tools.contains(DELEGATE_TOOL)
                && !tools.contains(CODING_FILE_TOOLS)
                && !tools.contains("mark_coding_complete");
    }

    public static boolean isReviewer(AgentDTO agent) {
        if (agent == null) {
            return false;
        }
        List<String> tools = agent.getAllowedTools();
        if (tools == null) {
            return false;
        }
        return tools.contains(ORCHESTRATION_READ_TOOLS)
                && !tools.contains(ORCHESTRATION_TASK_TOOLS)
                && !tools.contains(CODING_FILE_TOOLS);
    }

    public static boolean isWorker(AgentDTO agent) {
        if (agent == null) {
            return false;
        }
        List<String> tools = agent.getAllowedTools();
        if (tools == null) {
            return false;
        }
        return tools.contains(CODING_FILE_TOOLS) || tools.contains("mark_coding_complete");
    }

    public static List<Tool> filterToolsByRole(List<Tool> tools, AgentRole role) {
        Set<String> blocked = switch (role) {
            case SCHEDULER -> Set.of(
                    DELEGATE_TOOL,
                    "coding_subtask_tools",
                    CODING_FILE_TOOLS,
                    "coding_search_tools",
                    "coding_verify_tools",
                    ORCHESTRATION_SHELL_TOOLS,
                    "maven_command",
                    "mark_coding_complete"
            );
            case WORKER -> Set.of(
                    DELEGATE_TOOL,
                    "coding_subtask_tools",
                    ORCHESTRATION_TASK_TOOLS,
                    ORCHESTRATION_READ_TOOLS
            );
            case REVIEWER -> Set.of(
                    DELEGATE_TOOL,
                    "coding_subtask_tools",
                    ORCHESTRATION_TASK_TOOLS,
                    CODING_FILE_TOOLS,
                    "coding_search_tools",
                    "coding_verify_tools",
                    ORCHESTRATION_SHELL_TOOLS,
                    "maven_command",
                    "mark_coding_complete"
            );
            default -> Set.of(DELEGATE_TOOL, ORCHESTRATION_TASK_TOOLS);
        };
        return tools.stream()
                .filter(t -> !blocked.contains(t.getName()))
                .toList();
    }

    private static boolean hasTool(AgentDTO agent, String toolName) {
        if (agent == null || toolName == null) {
            return false;
        }
        List<String> tools = agent.getAllowedTools();
        return tools != null && tools.contains(toolName);
    }
}
