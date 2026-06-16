package com.kama.jchatmind.coding;

import com.kama.jchatmind.agent.profile.AgentProfile;
import com.kama.jchatmind.agent.profile.AgentProfileService;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.model.dto.AgentDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CodingAgentRoles {

    private static final String ORCHESTRATION_TASK_TOOLS = "orchestration_task_tools";
    private static final String ORCHESTRATION_READ_TOOLS = "orchestration_read_tools";
    private static final String CODING_FILE_TOOLS = "coding_file_tools";
    private static final String MARK_COMPLETE = "mark_coding_complete";
    private static final String DELEGATE_TOOL = "delegate_coding_task";
    private static final String CODING_SUBTASK_TOOLS = "coding_subtask_tools";

    public enum AgentRole { WORKER, REVIEWER, UNKNOWN }

    private static AgentProfileService profileService;
    private final AgentProfileService injectedProfileService;

    public CodingAgentRoles(AgentProfileService profileService) {
        this.injectedProfileService = profileService;
    }

    @PostConstruct
    void init() { profileService = this.injectedProfileService; }

    public static AgentRole resolveRole(AgentDTO agent) {
        if (isReviewer(agent)) return AgentRole.REVIEWER;
        if (isWorker(agent)) return AgentRole.WORKER;
        return AgentRole.UNKNOWN;
    }


    /** 兼容旧版 orchestrator 检测（delegate_coding_task + 无写文件工具） */
    public static boolean isOrchestrator(AgentDTO agent) {
        if (agent == null) return false;
        List<String> tools = agent.getAllowedTools();
        if (tools == null || tools.isEmpty()) return false;
        return tools.contains(DELEGATE_TOOL)
                && !tools.contains(CODING_FILE_TOOLS)
                && !tools.contains(MARK_COMPLETE);
    }

    public static boolean isReviewer(AgentDTO agent) {
        if (agent == null) return false;
        List<String> tools = agent.getAllowedTools();
        if (tools == null) return false;
        return tools.contains(ORCHESTRATION_READ_TOOLS)
                && !tools.contains(ORCHESTRATION_TASK_TOOLS)
                && !tools.contains(CODING_FILE_TOOLS);
    }

    public static boolean isWorker(AgentDTO agent) {
        if (agent == null) return false;
        List<String> tools = agent.getAllowedTools();
        if (tools == null) return false;
        return tools.contains(CODING_FILE_TOOLS) || tools.contains(MARK_COMPLETE);
    }

    public static List<Tool> filterToolsByRole(List<Tool> tools, AgentRole role) {
        Set<String> allowed = getAllowedToolsForRole(role);
        if (allowed == null) {
            return filterByFallbackBlocklist(tools, role);
        }
        return tools.stream()
                .filter(t -> allowed.contains(t.getName()))
                .toList();
    }

    public static String getSystemPromptForRole(AgentRole role) {
        if (profileService == null) return "";
        AgentProfile profile = switch (role) {
            case WORKER -> profileService.getWorkerProfile();
            case REVIEWER -> profileService.getReviewerProfile();
            default -> null;
        };
        return profile != null ? profile.getSystemPrompt() : "";
    }

    public static int getMaxStepsForRole(AgentRole role) {
        if (profileService == null) return 35;
        AgentProfile profile = switch (role) {
            case WORKER -> profileService.getWorkerProfile();
            case REVIEWER -> profileService.getReviewerProfile();
            default -> null;
        };
        return profile != null ? profile.getMaxSteps() : 35;
    }

    private static Set<String> getAllowedToolsForRole(AgentRole role) {
        if (profileService == null) return null;
        AgentProfile profile = switch (role) {
            case WORKER -> profileService.getWorkerProfile();
            case REVIEWER -> profileService.getReviewerProfile();
            default -> null;
        };
        if (profile == null || profile.getAllowedTools() == null) return null;
        return profile.getAllowedTools().stream().collect(Collectors.toUnmodifiableSet());
    }

    /** 回退：profileService 不可用时（如测试环境）使用硬编码黑名单 */
    private static List<Tool> filterByFallbackBlocklist(List<Tool> tools, AgentRole role) {
        Set<String> blocked = switch (role) {
            case WORKER -> Set.of(
                    DELEGATE_TOOL, CODING_SUBTASK_TOOLS, ORCHESTRATION_TASK_TOOLS, ORCHESTRATION_READ_TOOLS
            );
            case REVIEWER -> Set.of(
                    DELEGATE_TOOL, CODING_SUBTASK_TOOLS, ORCHESTRATION_TASK_TOOLS,
                    CODING_FILE_TOOLS, "coding_search_tools", "coding_verify_tools",
                    "orchestration_shell_tools", "maven_command", MARK_COMPLETE
            );
            default -> Set.of(DELEGATE_TOOL, ORCHESTRATION_TASK_TOOLS);
        };
        return tools.stream()
                .filter(t -> !blocked.contains(t.getName()))
                .toList();
    }

    private static boolean hasTool(AgentDTO agent, String toolName) {
        if (agent == null || toolName == null) return false;
        List<String> tools = agent.getAllowedTools();
        return tools != null && tools.contains(toolName);
    }
}
