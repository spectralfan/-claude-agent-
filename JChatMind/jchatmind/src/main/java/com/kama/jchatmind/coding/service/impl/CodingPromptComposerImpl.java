package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingPromptComposer;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.ProjectRulesService;
import com.kama.jchatmind.model.dto.AgentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodingPromptComposerImpl implements CodingPromptComposer {

    private final CodingTaskService codingTaskService;
    private final ProjectRulesService projectRulesService;

    @Override
    public String composeSystemPrompt(String basePrompt, String taskSessionId, AgentDTO agentConfig) {
        CodingTask task = codingTaskService.getActiveTask(taskSessionId);
        if (task != null) {
            return composeForTask(task, basePrompt);
        }
        final String initPrompt = basePrompt == null ? "" : basePrompt;
        return projectRulesService.getRules()
                .map(rules -> initPrompt + "\n\n## \u9879\u76EE\u89C4\u5219\n" + rules)
                .orElse(initPrompt);
    }

    @Override
    public String composeForTask(CodingTask task, String basePrompt) {
        final String initialPrompt = basePrompt == null ? "" : basePrompt;
        String prompt = projectRulesService.getRulesForTask(task)
                .map(rules -> initialPrompt + "\n\n## \u9879\u76EE\u89C4\u5219\n" + rules)
                .orElse(initialPrompt);
        return prompt + buildTaskContextBlock(task);
    }

    private String buildTaskContextBlock(CodingTask task) {
        return """

                ## Coding Task Context
                - taskId: %s
                - workspace root: %s
                - workspace path: %s
                """.formatted(
                task.getId(),
                task.getWorkspaceRoot() != null ? task.getWorkspaceRoot() : "(default)",
                task.getWorkspacePath() != null ? task.getWorkspacePath() : "."
        );
    }
}