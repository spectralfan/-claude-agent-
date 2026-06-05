package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingMessageEnricher;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.ProjectRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectRulesServiceImpl implements ProjectRulesService {

    private final CodingProperties codingProperties;
    private final CodingWorkspaceService workspaceService;

    @Override
    public Optional<String> getRules() {
        return readRulesFromRoot(workspaceService.getWorkspaceRoot());
    }

    @Override
    public Optional<String> getRulesForTask(CodingTask task) {
        if (task == null) {
            return getRules();
        }
        return readRulesFromRoot(workspaceService.resolveForTask(task));
    }

    private Optional<String> readRulesFromRoot(Path root) {
        if (!codingProperties.getProjectRules().isEnabled()) {
            return Optional.empty();
        }
        for (String file : codingProperties.getProjectRules().getFiles()) {
            Path p = root.resolve(file);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                try {
                    String content = Files.readString(p);
                    return Optional.of(truncate(content, codingProperties.getProjectRules().getMaxChars()));
                } catch (IOException ignore) {
                    // 尝试下一个规则文件
                }
            }
        }
        return Optional.empty();
    }

    private String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n[项目规则已截断]";
    }
}
