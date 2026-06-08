package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.CodingAgentRoles;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingPromptComposer;
import com.kama.jchatmind.coding.service.CodingSkillService;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.ProjectRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodingPromptComposerImpl implements CodingPromptComposer {

    private final CodingTaskService codingTaskService;
    private final ProjectRulesService projectRulesService;
    private final CodingProperties codingProperties;
    private final CodingSkillService codingSkillService;
    private final CodingStackService codingStackService;

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
        if (CodingAgentRoles.isOrchestrator(agentConfig)) {
            return prompt + buildOrchestratorContextBlock(task);
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

    private String buildOrchestratorContextBlock(CodingTask task) {
        return """

                ## Coding 任务上下文（编排模式）
                - taskId: %s
                - 工作区根: %s
                - 子路径: %s

                ## 编排协议（严禁违反）
                1. **你只负责编排**：禁止改代码、禁止调用 coding_file_tools / coding_search_tools / run_terminal_cmd / bash / maven_command / mark_coding_complete
                2. 用 delegate_coding_task(goal, title) 将每个可验收子目标委派给 Worker
                3. 用 get_coding_subtask_status 轮询；status=FAILED 时阅读 errorMessage，修正 goal 后**重新委派**，不得亲自执行开发或验证
                4. 所有子任务 COMPLETED 后，向用户汇总完成了什么、如何验证
                """.formatted(
                task.getId(),
                task.getWorkspaceRoot() != null ? task.getWorkspaceRoot() : "(默认)",
                task.getWorkspacePath() != null ? task.getWorkspacePath() : "."
        );
    }

    private String buildCodingAutonomousBlock(CodingTask task) {
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
                : "改代码后优先 coding_verify_tools（run_allowed_verify / check_js_syntax）；简单命令才用 MCP";
        String doneCriteria = stack != null && stack.getDoneCriteria() != null
                ? stack.getDoneCriteria()
                : "验证命令 exit 0";

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
                
                8. **验证（根治）**：优先 **coding_verify_tools**（check_js_syntax / verify_coding_file / run_allowed_verify），不经 MCP 拼 node -e、import 多行脚本。
                9. **MCP 终端**：仅简单命令（npm test、dir）；必须设 workingDir=工作区根；禁止 http-server、禁止占 8080。
                   HTML 预览请用户浏览器直接打开，或本地手动 `npx http-server -p %d`。""".formatted(previewPort);

        return """

                ## Coding 任务上下文（Claude Code 模式）
                - taskId: %s
                - 工作区根: %s
                - 子路径: %s
                %s%s- 审批模式: %s
                - 推荐工具: %s

                ## 自主开发协议
                1. 使用 coding_file_tools 读写文件；大 HTML/JS **禁止单次 write 整文件**（易 JSON 截断），先写骨架再 append_coding_file / apply_coding_patch 分块
                2. coding_search_tools 搜索/局部 patch
                3. 验证：%s
                4. 结构化验证用 coding_verify_tools；仅简单命令才用 MCP（%s）
                5. 同一用户请求内连续完成全流程，不要中途停下等用户再发消息
                6. 用户消息中的 @文件 已附带 <file> 内容，可直接引用
                7. 完成标准：%s；验证 exit 0 后调用 mark_coding_complete(summary)%s%s
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
                mavenFallback,
                pythonUvHint,
                windowsShellHint
        );
    }
}
