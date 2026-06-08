package com.kama.jchatmind.coding.bridge;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 当 Coding 会话存在时，将 MCP shell 类工具输出桥接到 CODING_COMMAND_OUTPUT SSE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodingMcpOutputBridge {

    private static final Set<String> TERMINAL_TOOLS = Set.of(
            "run_terminal_cmd", "bash", "shell", "shell_exec", "shell_execute",
            "execute_command", "terminal"
    );

    private final CodingTaskService codingTaskService;
    private final ChatEventPublisher chatEventPublisher;
    private final CodingProperties codingProperties;
    private final CodingVerificationService codingVerificationService;

    public void onToolResult(String toolName, String toolInput, String output) {
        if (toolName == null || output == null) {
            return;
        }
        if (!isTerminalTool(toolName)) {
            return;
        }
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return;
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return;
        }
        String command = extractCommandPreview(toolName, toolInput);
        int maxChars = codingProperties.getCommand().getOutputMaxChars();
        String clipped = output.length() > maxChars
                ? output.substring(0, maxChars) + "\n...(输出已截断)"
                : output;
        int exitCode = inferExitCode(output);
        try {
            chatEventPublisher.publish(task.getSessionId(), SseMessage.builder()
                    .type(SseMessage.Type.CODING_COMMAND_OUTPUT)
                    .payload(SseMessage.Payload.builder()
                            .taskId(task.getId())
                            .command(command)
                            .exitCode(exitCode)
                            .output(clipped)
                            .done(exitCode == 0)
                            .build())
                    .build());
            codingTaskService.recordExecutionResult(task.getId(), command, clipped);
            if (exitCode == 0) {
                codingVerificationService.recordSuccess(task.getId(), command, 0);
            }
        } catch (Exception e) {
            log.warn("MCP→Coding 终端 SSE 失败: {}", e.getMessage());
        }
    }

    private boolean isTerminalTool(String toolName) {
        String lower = toolName.toLowerCase(Locale.ROOT);
        if (TERMINAL_TOOLS.contains(lower)) {
            return true;
        }
        for (String terminal : TERMINAL_TOOLS) {
            if (lower.endsWith("_" + terminal) || lower.contains(terminal)) {
                return true;
            }
        }
        return lower.contains("terminal") || lower.contains("shell");
    }

    private String extractCommandPreview(String toolName, String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return toolName;
        }
        String trimmed = toolInput.trim();
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "...";
        }
        return trimmed;
    }

    private int inferExitCode(String output) {
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
        if (lower.contains("调用失败") || lower.contains("\"iserror\":true") || lower.contains("iserror: true")) {
            return 1;
        }
        if (lower.startsWith("error:") || lower.contains("mcp 工具[") && lower.contains("调用失败")) {
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
