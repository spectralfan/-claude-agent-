package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 程序化调用 MCP execute_command，复用 RecordingToolCallback 中的 Enricher + Policy 层。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpShellExecutor {

    private static final List<String> SHELL_TOOL_PRIORITY = List.of(
            "execute_command", "shell_exec", "shell_execute"
    );

    private final McpToolBridge mcpToolBridge;
    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;

    /**
     * @return empty 表示 MCP 不可用或调用异常，由调用方降级 sandbox
     */
    public Optional<McpShellResult> execute(String command, String workingDir) {
        if (!mcpProperties.isEnabled() || command == null || command.isBlank()) {
            return Optional.empty();
        }
        ToolCallback shellTool = resolveShellTool();
        if (shellTool == null) {
            log.debug("MCP shell 工具不可用，跳过程序化执行");
            return Optional.empty();
        }
        try {
            String toolInput = buildToolInput(command.trim(), workingDir);
            String output = shellTool.call(toolInput);
            return Optional.of(new McpShellResult(inferExitCode(output), output));
        } catch (Exception e) {
            log.warn("MCP shell 程序化调用失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return mcpProperties.isEnabled() && resolveShellTool() != null;
    }

    private ToolCallback resolveShellTool() {
        List<ToolCallback> callbacks = mcpToolBridge.getAllToolCallbacks();
        if (callbacks.isEmpty()) {
            return null;
        }
        return callbacks.stream()
                .filter(cb -> SHELL_TOOL_PRIORITY.contains(cb.getToolDefinition().name()))
                .min(Comparator.comparingInt(cb -> SHELL_TOOL_PRIORITY.indexOf(cb.getToolDefinition().name())))
                .orElse(null);
    }

    private String buildToolInput(String command, String workingDir) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("command", command);
        if (workingDir != null && !workingDir.isBlank()) {
            node.put("workingDir", workingDir);
        }
        return objectMapper.writeValueAsString(node);
    }

    static int inferExitCode(String output) {
        if (output == null || output.isBlank()) {
            return 1;
        }
        String lower = output.toLowerCase(Locale.ROOT);
        if (containsExplicitExitCode(lower, 1)) {
            return 1;
        }
        if (containsExplicitExitCode(lower, 0)) {
            return 0;
        }
        if (lower.contains("策略拦截") || lower.contains("调用失败")
                || lower.contains("\"iserror\":true") || lower.contains("iserror: true")) {
            return 1;
        }
        if (lower.contains("command failed") || lower.contains("command not found")) {
            return 1;
        }
        return 0;
    }

    private static boolean containsExplicitExitCode(String lower, int code) {
        return lower.contains("exit code: " + code)
                || lower.contains("exitcode=" + code)
                || lower.contains("exit code " + code);
    }
}
