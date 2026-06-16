package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.mcp.permission.PermissionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 内置 Bash 执行工具（对齐 KamaClaude BashTool）。
 * 不依赖 MCP shell proxy，直接用 ProcessBuilder 在 coding workspace 下执行命令。
 */
@Slf4j
@Component
public class BashTool implements Tool {

    private static final int MAX_OUTPUT_BYTES = 64 * 1024;  // 64KB 截断
    private static final int TIMEOUT_SECONDS = 120;

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService workspaceService;
    private final PermissionManager permissionManager;

    public BashTool(CodingTaskService codingTaskService,
                    CodingWorkspaceService workspaceService,
                    PermissionManager permissionManager) {
        this.codingTaskService = codingTaskService;
        this.workspaceService = workspaceService;
        this.permissionManager = permissionManager;
    }

    @Override public String getName() { return "execute_command"; }
    @Override public String getDescription() {
        return "Execute a shell command in the coding workspace. Use this to run tests, lint, build, or inspect files.";
    }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(
            name = "execute_command",
            description = "Execute a shell command in the coding workspace. "
                    + "Use this to run tests (mvn test, npm test), lint (npm run lint), "
                    + "build (mvn compile, npm run build), or inspect files (ls, cat, wc). "
                    + "The command runs in the current coding workspace directory."
    )
    public String execute(String command) {
        if (command == null || command.isBlank()) {
            return "错误：command 不能为空\nexit code: 1";
        }
        command = command.trim();

        // 安全策略：拦截高危命令
        String blockReason = checkDangerous(command);
        if (blockReason != null) {
            return "命令被拦截：" + blockReason + "\nexit code: 1";
        }

        Path workspace = resolveWorkspace();
        File workDir = workspace != null ? workspace.toFile() : new File(".");

        try {
            ProcessBuilder pb = buildProcess(command, workDir);
            Process process = pb.start();

            // 读取输出（分离合并的 stderr+stdout）
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            Thread outThread = transferAsync(process.getInputStream(), out);
            Thread errThread = transferAsync(process.getErrorStream(), err);
            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outThread.join(5000);
            errThread.join(5000);

            if (!finished) {
                process.destroyForcibly();
                return "命令执行超时 (" + TIMEOUT_SECONDS + "s)\nexit code: 1";
            }

            int exitCode = process.exitValue();
            StringBuilder result = new StringBuilder();
            String errStr = err.toString(StandardCharsets.UTF_8);
            if (!errStr.isEmpty()) {
                result.append("stderr:\n").append(truncate(errStr)).append("\n");
            }
            String outStr = out.toString(StandardCharsets.UTF_8);
            if (!outStr.isEmpty()) {
                if (result.length() > 0) result.append("\n");
                result.append(truncate(outStr));
            }
            if (result.length() == 0) {
                result.append("(无输出)");
            }
            result.append("\nexit code: ").append(exitCode);
            return result.toString();
        } catch (Exception e) {
            log.warn("BashTool 执行失败: {}", e.getMessage());
            return "命令执行异常: " + e.getMessage() + "\nexit code: 1";
        }
    }

    /** 构建跨平台 ProcessBuilder */
    private ProcessBuilder buildProcess(String command, File workDir) {
        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("bash", "-c", command);
        }
        pb.directory(workDir);
        pb.redirectErrorStream(false);
        return pb;
    }

    /** 异步读取流 */
    private Thread transferAsync(java.io.InputStream in, ByteArrayOutputStream out) {
        return new Thread(() -> {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            try {
                while ((n = in.read(buf)) != -1 && total < MAX_OUTPUT_BYTES) {
                    int toWrite = Math.min(n, MAX_OUTPUT_BYTES - total);
                    out.write(buf, 0, toWrite);
                    total += toWrite;
                }
            } catch (Exception ignored) {}
        });
    }

    /** 截断过长输出 */
    private String truncate(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_OUTPUT_BYTES) return s;
        int cut = MAX_OUTPUT_BYTES - 50;
        return new String(bytes, 0, cut, StandardCharsets.UTF_8)
                + "\n... (截断，原始输出 " + bytes.length + " 字节)";
    }

    /** 解析 coding workspace 绝对路径 */
    private Path resolveWorkspace() {
        try {
            CodingSessionContext.Context ctx = CodingSessionContext.get();
            if (ctx == null) return null;
            CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
            if (task == null) return null;
            return workspaceService.resolveForTask(task);
        } catch (Exception e) {
            return null;
        }
    }

    /** 高危命令检测（对齐 McpShellCommandPolicy） */
    private String checkDangerous(String command) {
        String lower = command.toLowerCase();
        if (lower.contains("rm -rf /") || lower.contains("rm -rf ~")
                || lower.contains("rm -rf .")) {
            return "禁止递归删除（rm -rf）";
        }
        if (lower.contains("> /dev/") || lower.contains("dd if=")) {
            return "禁止磁盘写入操作";
        }
        if (lower.contains(":(){ :|:& };:")) {
            return "禁止 fork bomb";
        }
        return null;
    }
}