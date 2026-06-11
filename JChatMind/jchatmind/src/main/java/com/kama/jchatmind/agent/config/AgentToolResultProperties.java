package com.kama.jchatmind.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具结果上下文压缩配置，前缀 agent.tool-result。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.tool-result")
public class AgentToolResultProperties {

    private boolean enabled = true;

    /** 最近 K 个 tool round 保持完整 responseData */
    private int preserveRecentRounds = 2;

    private int defaultMaxChars = 2000;

    private int headTailMaxChars = 800;

    private List<String> statusOnlyTools = new ArrayList<>(List.of(
            "check_js_syntax",
            "verify_coding_file",
            "list_stack_verify_commands",
            "run_stack_verify",
            "run_allowed_verify",
            "write_coding_file",
            "append_coding_file",
            "mark_coding_complete",
            "KnowledgeTool"
    ));

    private List<String> headTailTools = new ArrayList<>(List.of(
            "run_terminal_cmd",
            "execute_command",
            "maven_command",
            "run_workspace_shell",
            "bash",
            "shell",
            "shell_exec",
            "shell_execute"
    ));

    private List<String> maxCharsTools = new ArrayList<>(List.of(
            "read_coding_file",
            "read_coding_files",
            "search_coding_files",
            "list_coding_files",
            "list_coding_directory_tree",
            "list_workspace_dir",
            "list_orchestration_tasks"
    ));
}
