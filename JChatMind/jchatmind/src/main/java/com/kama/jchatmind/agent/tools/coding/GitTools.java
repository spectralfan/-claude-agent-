package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CommandExecutionResult;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.SandboxCommandRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class GitTools implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final SandboxCommandRunner sandboxCommandRunner;

    public GitTools(CodingTaskService cts, CodingWorkspaceService cws, SandboxCommandRunner scr) {
        this.codingTaskService = cts;
        this.codingWorkspaceService = cws;
        this.sandboxCommandRunner = scr;
    }

    public String getName() { return "git_tool"; }
    public String getDescription() { return "Git 版本管理：status/diff/log/commit"; }
    public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(name = "git_status", description = "显示工作区 Git 状态")
    public String gitStatus() { return runGit("status", "--short"); }

    @org.springframework.ai.tool.annotation.Tool(name = "git_diff", description = "显示工作区未暂存的 diff")
    public String gitDiff() { return runGit("diff"); }

    @org.springframework.ai.tool.annotation.Tool(name = "git_log", description = "显示最近提交历史")
    public String gitLog(int limit) {
        int n = limit > 0 ? Math.min(limit, 50) : 10;
        return runGit("log", "--oneline", "-" + n);
    }

    @org.springframework.ai.tool.annotation.Tool(name = "git_commit", description = "提交当前所有修改")
    public String gitCommit(String message) {
        if (message == null || message.isBlank()) return "错误：message 不能为空";
        runGit("add", "-A");
        return runGit("commit", "-m", message);
    }

    private String runGit(String... args) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) return "错误：无 Coding 会话上下文";
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) return "错误：当前会话无活动 Coding 任务";
        Path workspace = codingWorkspaceService.resolveForTask(task);
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add("git");
        java.util.Collections.addAll(cmd, args);
        CommandExecutionResult r = sandboxCommandRunner.run(cmd, workspace, 30, 4096);
        String out = "exitCode=" + r.getExitCode();
        if (r.getOutput() != null && !r.getOutput().isBlank()) out = out + "\n" + r.getOutput();
        return out;
    }
}