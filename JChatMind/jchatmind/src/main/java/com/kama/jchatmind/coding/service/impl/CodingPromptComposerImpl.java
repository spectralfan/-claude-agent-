package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.CodingAgentRoles;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.config.OrchestrationProperties;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingPromptComposer;
import com.kama.jchatmind.coding.service.CodingSkillService;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.ProjectRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodingPromptComposerImpl implements CodingPromptComposer {

    private final CodingTaskService codingTaskService;
    private final ProjectRulesService projectRulesService;
    private final CodingProperties codingProperties;
    private final CodingSkillService codingSkillService;
    private final CodingStackService codingStackService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final OrchestrationProperties orchestrationProperties;

    @Override
    public String composeSystemPrompt(String basePrompt, String taskSessionId, AgentDTO agentConfig) {
        CodingTask task = codingTaskService.getActiveTask(taskSessionId);
        if (task != null) {
            return composeForTask(task, basePrompt, agentConfig);
        }
        String prompt = basePrompt == null ? "" : basePrompt;
        return projectRulesService.getRules()
                .map(rules -> prompt + "\n\n## 项目规则\n" + rules)
                .orElse(prompt);
    }

    @Override
    public String composeForTask(CodingTask task, String basePrompt, AgentDTO agentConfig) {
        String prompt = basePrompt == null ? "" : basePrompt;
        String beforeRules = prompt;
        prompt = projectRulesService.getRulesForTask(task)
                .map(rules -> beforeRules + "\n\n## 项目规则\n" + rules)
                .orElse(beforeRules);
        if (CodingAgentRoles.isOrchestrator(agentConfig) || CodingAgentRoles.isScheduler(agentConfig)) {
            return prompt + buildSchedulerContextBlock(task);
        }
        prompt = appendSkillPrompt(prompt, task);
        return prompt + buildCodingAutonomousBlock(task);
    }

    private String appendSkillPrompt(String prompt, CodingTask task) {
        if (!codingProperties.getSkills().isEnabled()) {
            return prompt;
        }
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        String skillId = metadata.getSkillId();
        if ((skillId == null || skillId.isBlank()) && metadata.getStackId() != null) {
            skillId = codingStackService.findById(metadata.getStackId())
                    .map(CodingStackDTO::getSkillId)
                    .orElse(null);
        }
        if (skillId == null || skillId.isBlank()) {
            return prompt;
        }
        final String resolvedSkillId = skillId;
        return codingSkillService.findById(resolvedSkillId)
                .map(skill -> prompt + "\n\n## Skill: " + skill.getName() + "\n" + skill.getPrompt())
                .orElse(prompt);
    }


    private String buildContextFilesBlock(CodingTask task, List<String> contextFiles) {
        if (contextFiles == null || contextFiles.isEmpty() || task == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n## 上下文文件\n");
        Path base = codingWorkspaceService.resolveForTask(task);
        int maxBytes = orchestrationProperties.getReadMaxBytes();
        for (String rel : contextFiles) {
            if (rel == null || rel.isBlank()) {
                continue;
            }
            try {
                Path target = base.resolve(rel).normalize();
                if (!codingWorkspaceService.isPathSafe(base, target)) {
                    sb.append("<file path=\"").append(rel).append("\" error=\"路径越界\"/>\n");
                    continue;
                }
                if (!Files.exists(target) || !Files.isRegularFile(target)) {
                    sb.append("<file path=\"").append(rel).append("\" error=\"不存在\"/>\n");
                    continue;
                }
                byte[] bytes = Files.readAllBytes(target);
                boolean truncated = bytes.length > maxBytes;
                String content = new String(
                        truncated ? java.util.Arrays.copyOf(bytes, maxBytes) : bytes,
                        StandardCharsets.UTF_8);
                sb.append("<file path=\"").append(rel).append("\" truncated=\"")
                        .append(truncated).append("\">\n");
                sb.append(content).append("\n</file>\n");
            } catch (Exception e) {
                sb.append("<file path=\"").append(rel).append("\" error=\"")
                        .append(e.getMessage()).append("\"/>\n");
            }
        }
        return sb.toString();
    }

    private String buildSchedulerContextBlock(CodingTask task) {
        return """

                ## Coding 任务上下文（Scheduler 编排模式）
                - taskId: %s
                - 工作区根: %s
                - 子路径: %s

                ## 编排协议（严禁违反）
                1. **你只负责调度**：禁止改代码、禁止 shell；用 orchestration_read_tools 只读辅助规划
                2. **批量建图**：同一轮可多个 create_orchestration_task；大需求必须拆多个 WORKER，无 dependsOn 则并行
                3. **Worker goal** 须含：改哪些文件、验收命令、完成标准（exit 0 + 行为）
                4. Worker COMPLETED 后系统**自动**插入 Reviewer（coding.orchestration.auto-review）；一般无需手建 REVIEWER
                5. **禁止 list 轮询**：全部 create 后用文字收手；仅在 [系统自动继续] 或用户追问时再 list（最多一次）
                6. list 时终态任务附带 resultSummary / errorMessage
                7. Worker FAILED → create 修复 Worker；Reviewer VERDICT: FAIL → create 修复 Worker
                8. 全部终态后向用户汇总并 mark_coding_complete
                """.formatted(
                task.getId(),
                task.getWorkspaceRoot() != null ? task.getWorkspaceRoot() : "(默认)",
                task.getWorkspacePath() != null ? task.getWorkspacePath() : "."
        );
    }

    private String buildWorkerSubtaskBlock() {
        return """

                ## Worker 子任务协议
                - 仅完成上述 goal 与 constraints，不扩大 scope
                - **禁止** mark_coding_complete、禁止 create_orchestration_task
                - 最后一条 assistant 消息必须包含：
                  ## 交付摘要
                  - 修改文件: ...
                  - 验证命令与结果: ...
                  - 完成情况: ...
                """;
    }

    private String buildReviewerRoleBlock() {
        return """

                ## Reviewer 子任务协议
                - 只读：orchestration_read_tools（list_workspace_dir / read_workspace_file）
                - 对照 goal/constraints 检查质量、测试、安全、可维护性
                - 最后一条 assistant 消息必须包含（Scheduler 会解析 VERDICT）：
                  ## 审查结论
                  VERDICT: PASS | FAIL
                  ## 发现
                  - ...
                  ## 建议修复
                  - （VERDICT=FAIL 时必填）
                """;
    }

    private String buildCodingAutonomousBlock(CodingTask task) {
        return buildCodingAutonomousBlock(task, false);
    }

    private String buildCodingAutonomousBlock(CodingTask task, boolean orchestrationSubtask) {
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        String approvalHint = metadata.getApprovalMode() != null
                ? metadata.getApprovalMode().getCode()
                : codingProperties.getApproval().getDefaultMode().getCode();

        CodingStackDTO stack = null;
        if (metadata.getStackId() != null && !metadata.getStackId().isBlank()) {
            stack = codingStackService.findById(metadata.getStackId()).orElse(null);
        }

        String stackLine = stack != null
                ? "- 技术栈: " + stack.getDisplayName() + " (" + stack.getId() + ")\n"
                : "- 技术栈: 未预设（请先查看工作区 pom.xml / pyproject.toml / package.json 等判断语言与验证命令）\n";
        String languageLine = metadata.getLanguage() != null
                ? "- 语言: " + metadata.getLanguage() + "\n"
                : "";

        String verifyWorkflow = stack != null && stack.getVerifyWorkflow() != null
                ? stack.getVerifyWorkflow()
                : "list_stack_verify_commands → run_stack_verify(label)；HTML 用 verify_coding_file；仅 .js 用 check_js_syntax";
        String doneCriteria = stack != null && stack.getDoneCriteria() != null
                ? stack.getDoneCriteria()
                : "验证命令 exit 0";
        String completionStep = orchestrationSubtask
                ? "验证通过后输出 ## 交付摘要（子任务内**禁止** mark_coding_complete）"
                : "验证 exit 0 后调用 mark_coding_complete(summary)";

        String mcpTools = stack != null && stack.getSuggestedMcpTools() != null
                && !stack.getSuggestedMcpTools().isEmpty()
                ? String.join(", ", stack.getSuggestedMcpTools())
                : "run_terminal_cmd, bash";

        String agentTools = stack != null && stack.getSuggestedAgentTools() != null
                && !stack.getSuggestedAgentTools().isEmpty()
                ? String.join(", ", stack.getSuggestedAgentTools())
                : "coding_file_tools, coding_search_tools, coding_verify_tools, " + mcpTools;

        String mavenFallback = stack != null && "java".equalsIgnoreCase(stack.getLanguage())
                ? "\n7. 若无 MCP 终端工具，可降级使用 maven_command（compile/test）"
                : "";

        String pythonUvHint = stack != null && "python".equalsIgnoreCase(stack.getLanguage())
                ? "\n7. Python 依赖用 **uv**：uv sync / uv add / uv run pytest；勿用 pip；MCP 命令单行、无 bash 重定向（禁 &&、2>&1、|）；可设 workingDir 为工作区根"
                : "";

        int previewPort = codingProperties.getWorkspace().getPreviewPort();
        String windowsShellHint = """
                
                8. **验证**：**list_stack_verify_commands** → **run_stack_verify(label)**（栈配置，MCP 优先）；HTML 用 verify_coding_file；仅 .js 用 check_js_syntax；禁止对 .html 做语法检查。
                9. **MCP 终端**：仅简单辅助命令（dir、type）；必须设 workingDir=工作区根；禁止 http-server、禁止占 8080。
                   HTML 预览请用户浏览器直接打开，或本地手动 `npx http-server -p %d`。""".formatted(previewPort);

        return """

                ## Coding 任务上下文（Claude Code 模式）
                - taskId: %s
                - 工作区根: %s
                - 子路径: %s
                %s%s- 审批模式: %s
                - 推荐工具: %s

                ## 自主开发协议
                1. 使用 coding_file_tools 读写文件；**探索目录用 list_coding_directory_tree(maxDepth=2~5)**，勿逐层 list_coding_directory；**多读文件用 read_coding_files** 一次批量读取
                2. 同一推理轮可并行发起多个 tool_calls（如列目录树 + 批量读 pom.xml 与主类），减少循环次数
                3. 大 HTML/JS **禁止单次 write 整文件**（易 JSON 截断），先写骨架再 append_coding_file / apply_coding_patch 分块
                4. coding_search_tools 搜索/局部 patch
                5. 验证：%s
                6. 栈验证用 coding_verify_tools（list/run_stack_verify）；仅简单辅助命令才用 MCP（%s）
                7. 同一用户请求内连续完成全流程，不要中途停下等用户再发消息
                8. 用户消息中的 @文件 已附带 <file> 内容，可直接引用
                9. 完成标准：%s；%s%s%s
                """.formatted(
                task.getId(),
                task.getWorkspaceRoot() != null ? task.getWorkspaceRoot() : "(默认)",
                task.getWorkspacePath() != null ? task.getWorkspacePath() : ".",
                stackLine,
                languageLine,
                approvalHint,
                agentTools,
                verifyWorkflow,
                mcpTools,
                doneCriteria,
                completionStep,
                mavenFallback,
                pythonUvHint,
                windowsShellHint
        );
    }
}
