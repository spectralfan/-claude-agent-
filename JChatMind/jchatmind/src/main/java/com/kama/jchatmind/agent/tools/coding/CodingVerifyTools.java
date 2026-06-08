package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import com.kama.jchatmind.coding.config.CodingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化验证工具（根治方案）：不经 MCP 自由 shell 字符串，直接 ProcessBuilder 执行白名单操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodingVerifyTools implements Tool {

    private static final Pattern NPM_TEST = Pattern.compile("^npm\\s+test$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPM_RUN = Pattern.compile("^npm\\s+run\\s+([\\w:-]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UV_PYTEST = Pattern.compile("^uv\\s+run\\s+pytest(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_CHECK = Pattern.compile("^node\\s+--check\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_PATH = Pattern.compile("^[\\w./\\\\-]+$");

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final SandboxCommandRunner sandboxCommandRunner;
    private final CodingVerificationService codingVerificationService;
    private final CodingProperties codingProperties;

    @Override
    public String getName() {
        return "coding_verify_tools";
    }

    @Override
    public String getDescription() {
        return "Coding 结构化验证：JS 语法检查、文件存在确认、白名单测试命令（不经 MCP 自由 shell）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "check_js_syntax",
            description = "检查工作区内 JS 文件语法（node --check）。relativePath 如 js/game.js 或 tank-battle/js/constants.js"
    )
    public String checkJsSyntax(String relativePath) {
        return runNodeCheck(requireTask(), relativePath);
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "verify_coding_file",
            description = "确认工作区内文件存在且可读。relativePath 相对于任务工作区根"
    )
    public String verifyCodingFile(String relativePath) {
        try {
            Path file = resolveSafePath(requireTask(), relativePath);
            if (!Files.isRegularFile(file)) {
                return "exit code: 1\n文件不存在或不是普通文件: " + relativePath;
            }
            long size = Files.size(file);
            return "exit code: 0\nOK: " + relativePath + " 存在，大小 " + size + " 字节";
        } catch (Exception e) {
            return "exit code: 1\n" + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "run_allowed_verify",
            description = """
                    运行白名单验证命令（无 shell 拼字符串）。允许：
                    npm test | npm run <script> | uv run pytest ... | node --check <relativePath>
                    """
    )
    public String runAllowedVerify(String command) {
        CodingTask task = requireTask();
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return "exit code: 1\ncommand 不能为空";
        }
        List<String> processArgs = parseAllowedCommand(trimmed);
        if (processArgs == null) {
            return """
                    exit code: 1
                    命令不在白名单: %s
                    允许: npm test | npm run <script> | uv run pytest | node --check <path>
                    或改用 check_js_syntax / verify_coding_file
                    """.formatted(trimmed);
        }
        return formatResult(execute(task, processArgs, trimmed));
    }

    private String runNodeCheck(CodingTask task, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "exit code: 1\nrelativePath 不能为空";
        }
        if (!RELATIVE_PATH.matcher(relativePath).matches() || !relativePath.toLowerCase(Locale.ROOT).endsWith(".js")) {
            return "exit code: 1\n非法 JS 路径: " + relativePath;
        }
        try {
            resolveSafePath(task, relativePath);
        } catch (Exception e) {
            return "exit code: 1\n" + e.getMessage();
        }
        List<String> args = parseAllowedCommand("node --check " + relativePath);
        return formatResult(execute(task, args, "node --check " + relativePath));
    }

    private CommandExecutionResult execute(CodingTask task, List<String> processArgs, String commandLine) {
        Path workspace = codingWorkspaceService.resolveForTask(task);
        CommandExecutionResult result = sandboxCommandRunner.run(
                processArgs,
                workspace,
                codingProperties.getMaven().getTimeoutSeconds(),
                codingProperties.getCommand().getOutputMaxChars()
        );
        codingTaskService.recordExecutionResult(task.getId(), commandLine,
                result.getOutput());
        if (result.getExitCode() == 0) {
            codingVerificationService.recordSuccess(task.getId(), commandLine, 0);
        }
        return result;
    }

    private List<String> parseAllowedCommand(String command) {
        Matcher m;
        if (NPM_TEST.matcher(command).matches()) {
            return windowsWrap("npm", "test");
        }
        m = NPM_RUN.matcher(command);
        if (m.matches()) {
            return windowsWrap("npm", "run", m.group(1));
        }
        if (UV_PYTEST.matcher(command).matches()) {
            return windowsWrapParts(command.split("\\s+"));
        }
        m = NODE_CHECK.matcher(command);
        if (m.matches()) {
            String rel = m.group(1).trim();
            if (!RELATIVE_PATH.matcher(rel).matches()) {
                return null;
            }
            return windowsWrap("node", "--check", rel);
        }
        return null;
    }

    private List<String> windowsWrap(String... parts) {
        if (!isWindows()) {
            return List.of(parts);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd.exe");
        cmd.add("/c");
        for (String p : parts) {
            cmd.add(p.endsWith(".cmd") ? p : (p.equals("npm") || p.equals("npx") || p.equals("uv") ? p + ".cmd" : p));
        }
        return cmd;
    }

    private List<String> windowsWrapParts(String[] parts) {
        if (!isWindows()) {
            return List.of(parts);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd.exe");
        cmd.add("/c");
        for (String p : parts) {
            if ("npm".equals(p) || "npx".equals(p) || "uv".equals(p)) {
                cmd.add(p + ".cmd");
            } else {
                cmd.add(p);
            }
        }
        return cmd;
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

    private CodingTask requireTask() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            throw new IllegalStateException("无 Coding 会话上下文");
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            throw new IllegalStateException("当前会话无活动 Coding 任务");
        }
        return task;
    }

    private static String formatResult(CommandExecutionResult result) {
        int code = result.getExitCode();
        String out = result.getOutput() == null ? "" : result.getOutput().trim();
        return "exit code: " + code + (out.isEmpty() ? "" : "\n" + out);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
