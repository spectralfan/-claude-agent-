package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.config.VerifyBackend;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.dto.MavenGoal;
import com.kama.jchatmind.coding.model.dto.RunMavenRequest;
import com.kama.jchatmind.coding.model.dto.StackVerifyCommandDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.StackVerifyExecutor;
import com.kama.jchatmind.mcp.bridge.McpShellExecutor;
import com.kama.jchatmind.mcp.bridge.McpShellResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StackVerifyExecutorImpl implements StackVerifyExecutor {

    private static final Pattern RELATIVE_PATH = Pattern.compile("^[\\w./\\\\-]+$");

    private final CodingStackService codingStackService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingCommandService codingCommandService;
    private final CodingTaskService codingTaskService;
    private final CodingVerificationService codingVerificationService;
    private final McpShellExecutor mcpShellExecutor;
    private final CodingProperties codingProperties;

    @Override
    public String listVerifyCommands(CodingTask task) {
        List<StackVerifyCommandDTO> commands = resolveCommands(task);
        if (commands.isEmpty()) {
            return "当前栈未配置 verifyCommands。可用 verify_coding_file 确认文件，或 MCP 简单命令。";
        }
        StringBuilder sb = new StringBuilder("可用栈验证命令（用 run_stack_verify(label) 执行）:\n");
        for (StackVerifyCommandDTO cmd : commands) {
            sb.append("- label=\"").append(cmd.getLabel()).append("\" type=").append(cmd.getType());
            if ("shell".equalsIgnoreCase(cmd.getType()) && cmd.getCommand() != null) {
                sb.append(" command=").append(cmd.getCommand());
            } else if ("maven".equalsIgnoreCase(cmd.getType()) && cmd.getGoal() != null) {
                sb.append(" goal=").append(cmd.getGoal());
            } else if ("file".equalsIgnoreCase(cmd.getType())) {
                String path = cmd.getPath() != null ? cmd.getPath() : cmd.getCommand();
                if (path != null) {
                    sb.append(" path=").append(path);
                }
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    @Override
    public String runByLabel(CodingTask task, String label) {
        if (label == null || label.isBlank()) {
            return "exit code: 1\nlabel 不能为空";
        }
        Optional<StackVerifyCommandDTO> matched = findByLabel(task, label);
        if (matched.isEmpty()) {
            return "exit code: 1\n未找到验证命令 label=\"" + label.trim()
                    + "\"。请先 list_stack_verify_commands。";
        }
        return executeCommand(task, matched.get());
    }

    @Override
    public List<StackVerifyCommandDTO> resolveCommands(CodingTask task) {
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        if (metadata.getStackId() == null || metadata.getStackId().isBlank()) {
            return List.of();
        }
        return codingStackService.findById(metadata.getStackId())
                .map(CodingStackDTO::getVerifyCommands)
                .orElse(List.of());
    }

    @Override
    public Optional<StackVerifyCommandDTO> findByLabel(CodingTask task, String label) {
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        return resolveCommands(task).stream()
                .filter(cmd -> cmd.getLabel() != null
                        && cmd.getLabel().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    @Override
    public Optional<StackVerifyCommandDTO> findByShellCommand(CodingTask task, String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return Optional.empty();
        }
        String normalized = commandLine.trim().toLowerCase(Locale.ROOT);
        return resolveCommands(task).stream()
                .filter(cmd -> "shell".equalsIgnoreCase(cmd.getType())
                        && cmd.getCommand() != null
                        && cmd.getCommand().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    private String executeCommand(CodingTask task, StackVerifyCommandDTO cmd) {
        String type = cmd.getType() != null ? cmd.getType().trim().toLowerCase(Locale.ROOT) : "";
        return switch (type) {
            case "shell" -> runShell(task, cmd);
            case "maven" -> runMaven(task, cmd);
            case "file" -> verifyFile(task, cmd);
            default -> "exit code: 1\n不支持的验证类型: " + cmd.getType();
        };
    }

    private String runShell(CodingTask task, StackVerifyCommandDTO cmd) {
        String commandLine = cmd.getCommand();
        if (commandLine == null || commandLine.isBlank()) {
            return "exit code: 1\nshell 类型缺少 command";
        }
        VerifyBackend backend = VerifyBackend.fromConfig(codingProperties.getVerify().getBackend());
        if (backend != VerifyBackend.SANDBOX) {
            Optional<McpShellResult> mcpResult = runViaMcp(task, commandLine);
            if (mcpResult.isPresent()) {
                return formatMcpAndRecord(task, commandLine, mcpResult.get());
            }
            if (backend == VerifyBackend.MCP) {
                return "exit code: 1\nMCP 不可用，无法执行: " + commandLine;
            }
        }
        if (!codingProperties.getVerify().isFallbackEnabled() && backend == VerifyBackend.AUTO) {
            return "exit code: 1\nMCP 不可用且未启用 sandbox 降级";
        }
        CommandExecutionResult result = codingCommandService.executeShell(task.getId(), commandLine);
        return formatSandboxResult(result);
    }

    private String runMaven(CodingTask task, StackVerifyCommandDTO cmd) {
        MavenGoal goal = parseMavenGoal(cmd.getGoal());
        if (goal == null) {
            return "exit code: 1\nmaven 类型 goal 无效: " + cmd.getGoal();
        }
        String commandLine = "mvn " + goal.getCode().replace('_', ' ');
        VerifyBackend backend = VerifyBackend.fromConfig(codingProperties.getVerify().getBackend());
        if (backend != VerifyBackend.SANDBOX) {
            Optional<McpShellResult> mcpResult = runViaMcp(task, commandLine);
            if (mcpResult.isPresent()) {
                return formatMcpAndRecord(task, commandLine, mcpResult.get());
            }
            if (backend == VerifyBackend.MCP) {
                return "exit code: 1\nMCP 不可用，无法执行: " + commandLine;
            }
        }
        RunMavenRequest request = RunMavenRequest.builder()
                .taskId(task.getId())
                .goal(goal)
                .build();
        CommandExecutionResult result = codingCommandService.executeMaven(request);
        return formatSandboxResult(result);
    }

    private String verifyFile(CodingTask task, StackVerifyCommandDTO cmd) {
        String relativePath = cmd.getPath() != null ? cmd.getPath() : cmd.getCommand();
        if (relativePath == null || relativePath.isBlank()) {
            return "exit code: 1\nfile 类型缺少 path";
        }
        try {
            Path file = resolveSafePath(task, relativePath);
            if (!Files.isRegularFile(file)) {
                return "exit code: 1\n文件不存在或不是普通文件: " + relativePath;
            }
            long size = Files.size(file);
            String commandLine = "verify_file:" + relativePath;
            String output = "OK: " + relativePath + " 存在，大小 " + size + " 字节";
            codingTaskService.recordExecutionResult(task.getId(), commandLine, output);
            codingVerificationService.recordSuccess(task.getId(), commandLine, 0);
            return "exit code: 0\n" + output;
        } catch (Exception e) {
            return "exit code: 1\n" + e.getMessage();
        }
    }

    private Optional<McpShellResult> runViaMcp(CodingTask task, String commandLine) {
        try {
            String workingDir = codingWorkspaceService.resolveForTask(task).toString();
            return mcpShellExecutor.execute(commandLine, workingDir);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String formatMcpAndRecord(CodingTask task, String commandLine, McpShellResult result) {
        codingTaskService.recordExecutionResult(task.getId(), commandLine, result.output());
        if (result.success()) {
            codingVerificationService.recordSuccess(task.getId(), commandLine, 0);
        }
        return result.output() != null && result.output().contains("exit code:")
                ? result.output()
                : "exit code: " + result.exitCode()
                + (result.output() == null || result.output().isBlank() ? "" : "\n" + result.output());
    }

    private static String formatSandboxResult(CommandExecutionResult result) {
        int code = result.getExitCode();
        String out = result.getOutput() == null ? "" : result.getOutput().trim();
        return "exit code: " + code + (out.isEmpty() ? "" : "\n" + out);
    }

    private MavenGoal parseMavenGoal(String goal) {
        if (goal == null || goal.isBlank()) {
            return null;
        }
        String normalized = goal.trim().toLowerCase(Locale.ROOT);
        for (MavenGoal g : MavenGoal.values()) {
            if (g.getCode().equals(normalized)) {
                return g;
            }
        }
        return switch (normalized) {
            case "compile" -> MavenGoal.COMPILE;
            case "test" -> MavenGoal.TEST;
            case "package" -> MavenGoal.PACKAGE_SKIP_TESTS;
            default -> null;
        };
    }

    private Path resolveSafePath(CodingTask task, String relativePath) throws IOException {
        if (!RELATIVE_PATH.matcher(relativePath).matches()) {
            throw new IOException("非法相对路径: " + relativePath);
        }
        Path workspace = codingWorkspaceService.resolveForTask(task).toAbsolutePath().normalize();
        Path resolved = workspace.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IOException("路径越界: " + relativePath);
        }
        return resolved;
    }
}
