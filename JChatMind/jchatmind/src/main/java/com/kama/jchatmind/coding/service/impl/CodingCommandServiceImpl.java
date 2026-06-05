package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.MavenGoal;
import com.kama.jchatmind.coding.model.dto.RunMavenRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodingCommandServiceImpl implements CodingCommandService {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;
    private final CodingProperties codingProperties;
    private final SandboxCommandRunner sandboxCommandRunner;
    private final CodingVerificationService codingVerificationService;

    @Override
    public CommandExecutionResult executeMaven(RunMavenRequest request) {
        CodingTask task = codingTaskService.getTaskEntity(request.getTaskId());
        List<String> command = toCommand(request.getGoal(), request.getTestPattern());
        Path workspace = workspaceService.resolveForTask(task);
        String commandLine = String.join(" ", command);

        CommandExecutionResult result = sandboxCommandRunner.run(
                command,
                workspace,
                codingProperties.getMaven().getTimeoutSeconds(),
                codingProperties.getMaven().getOutputMaxChars()
        );
        if (result.isTimeout()) {
            codingTaskService.timeoutTask(task.getId());
        } else {
            String summary = result.getExitCode() == 0
                    ? "命令成功 (exit 0): " + commandLine
                    : "命令失败 (exit " + result.getExitCode() + "): " + commandLine;
            codingTaskService.recordExecutionResult(task.getId(), commandLine,
                    summary + "\n" + result.getOutput());
            if (result.getExitCode() == 0) {
                codingVerificationService.recordSuccess(task.getId(), commandLine, 0);
            }
        }
        return result;
    }

    @Override
    public CommandExecutionResult executeShell(String taskId, String commandLine) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        Path workspace = workspaceService.resolveForTask(task);
        List<String> command = List.of("cmd", "/c", commandLine);
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            command = List.of("sh", "-c", commandLine);
        }
        CommandExecutionResult result = sandboxCommandRunner.run(
                command,
                workspace,
                codingProperties.getMaven().getTimeoutSeconds(),
                codingProperties.getCommand().getOutputMaxChars()
        );
        if (result.isTimeout()) {
            codingTaskService.timeoutTask(task.getId());
        } else {
            String summary = result.getExitCode() == 0
                    ? "命令成功 (exit 0): " + commandLine
                    : "命令失败 (exit " + result.getExitCode() + "): " + commandLine;
            codingTaskService.recordExecutionResult(task.getId(), commandLine,
                    summary + "\n" + result.getOutput());
            if (result.getExitCode() == 0) {
                codingVerificationService.recordSuccess(task.getId(), commandLine, 0);
            }
        }
        return result;
    }

    private List<String> toCommand(MavenGoal goal, String testPattern) {
        return switch (goal) {
            case COMPILE -> List.of("mvn", "compile");
            case TEST -> List.of("mvn", "test");
            case TEST_SINGLE -> List.of("mvn", "test", "-Dtest=" + testPattern);
            case PACKAGE_SKIP_TESTS -> List.of("mvn", "package", "-DskipTests");
            case CLEAN_COMPILE -> List.of("mvn", "clean", "compile");
            case CLEAN_TEST -> List.of("mvn", "clean", "test");
        };
    }
}
