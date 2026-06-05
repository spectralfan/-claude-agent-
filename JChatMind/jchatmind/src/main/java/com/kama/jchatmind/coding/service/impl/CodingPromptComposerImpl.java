package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
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
    public String composeSystemPrompt(String basePrompt, String taskSessionId) {
        CodingTask task = codingTaskService.getActiveTask(taskSessionId);
        if (task != null) {
            return composeForTask(task, basePrompt);
        }
        String prompt = basePrompt == null ? "" : basePrompt;
        return projectRulesService.getRules()
                .map(rules -> prompt + "\n\n## 项目规则\n" + rules)
                .orElse(prompt);
    }

    @Override
    public String composeForTask(CodingTask task, String basePrompt) {
        String prompt = basePrompt == null ? "" : basePrompt;
        String beforeRules = prompt;
        prompt = projectRulesService.getRulesForTask(task)
                .map(rules -> beforeRules + "\n\n## 项目规则\n" + rules)
                .orElse(beforeRules);
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
                : "";
        String languageLine = metadata.getLanguage() != null
                ? "- 语言: " + metadata.getLanguage() + "\n"
                : "";

        String verifyWorkflow = stack != null && stack.getVerifyWorkflow() != null
                ? stack.getVerifyWorkflow()
                : "改代码后通过 MCP 终端工具执行验证命令；失败则读输出、修复、再试";
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
                : "coding_file_tools, coding_search_tools, " + mcpTools;

        String mavenFallback = stack != null && "java".equalsIgnoreCase(stack.getLanguage())
                ? "\n7. 若无 MCP 终端工具，可降级使用 maven_command（compile/test）"
                : "";

        String pythonUvHint = stack != null && "python".equalsIgnoreCase(stack.getLanguage())
                ? "\n7. Python 依赖用 **uv**：uv sync / uv add / uv run pytest；勿用 pip；MCP 命令单行、无 bash 重定向（禁 &&、2>&1、|）；可设 workingDir 为工作区根"
                : "";

        return """

                ## Coding 任务上下文（Claude Code 模式）
                - taskId: %s
                - 工作区根: %s
                - 子路径: %s
                %s%s- 审批模式: %s
                - 推荐工具: %s

                ## 自主开发协议
                1. 使用 coding_file_tools 读写文件；coding_search_tools 搜索/局部 patch
                2. 验证：%s
                3. 优先通过 MCP 工具（%s）在工作区目录执行 shell 命令
                4. 同一用户请求内连续完成全流程，不要中途停下等用户再发消息
                5. 用户消息中的 @文件 已附带 <file> 内容，可直接引用
                6. 完成标准：%s；验证 exit 0 后调用 mark_coding_complete(summary)%s%s
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
                pythonUvHint
        );
    }
}
