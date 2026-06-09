package com.kama.jchatmind.agent.tools.orchestration;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.config.OrchestrationProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrchestrationShellTools implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final SandboxCommandRunner sandboxCommandRunner;
    private final CodingProperties codingProperties;
    private final OrchestrationProperties orchestrationProperties;

    @Override
    public String getName() {
        return "orchestration_shell_tools";
    }

    @Override
    public String getDescription() {
        return "在工作区执行 Shell 命令（仅 Worker）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "run_workspace_shell",
            description = "在工作区根目录执行 Shell 命令，输出上限 64KB。仅 Worker 可用。"
    )
    public String runWorkspaceShell(String command) {
        if (command == null || command.isBlank()) {
            return "错误：command 不能为空";
        }
        if (SubAgentRunContext.get() == null) {
            return "错误：run_workspace_shell 仅 Worker 子任务可用";
        }
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return "错误：无 Coding 会话上下文";
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return "错误：当前会话无活动 Coding 任务";
        }
        Path workspace = codingWorkspaceService.resolveForTask(task);
        List<String> cmd = buildShellCommand(command.trim());
        CommandExecutionResult result = sandboxCommandRunner.run(
                cmd,
                workspace,
                codingProperties.getMaven().getTimeoutSeconds(),
                orchestrationProperties.getShellOutputMaxChars()
        );
        return "exitCode=" + result.getExitCode()
                + (result.isTimeout() ? " (timeout)" : "")
                + "\n" + result.getOutput();
    }

    private List<String> buildShellCommand(String commandLine) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("cmd", "/c", commandLine);
        }
        return List.of("sh", "-c", commandLine);
    }
}
