package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.dto.MavenGoal;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingApprovalMode;
import org.springframework.stereotype.Component;

@Component
public class CodingApprovalPolicy {

    private final CodingProperties codingProperties;

    public CodingApprovalPolicy(CodingProperties codingProperties) {
        this.codingProperties = codingProperties;
    }

    public boolean needApproval(CodingTask task, MavenGoal goal) {
        if (!codingProperties.getApproval().isEnabled()) {
            return false;
        }
        CodingApprovalMode mode = resolveMode(task);
        return switch (mode) {
            case STRICT -> goal != MavenGoal.COMPILE;
            case DEVELOPMENT -> !isDevelopmentAutoGoal(goal);
            case TRUSTED -> false;
        };
    }

    public CodingApprovalMode resolveMode(CodingTask task) {
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        if (metadata.getApprovalMode() != null) {
            return metadata.getApprovalMode();
        }
        return codingProperties.getApproval().getDefaultMode();
    }

    private boolean isDevelopmentAutoGoal(MavenGoal goal) {
        return switch (goal) {
            case COMPILE, TEST, TEST_SINGLE, CLEAN_COMPILE, CLEAN_TEST -> true;
            case PACKAGE_SKIP_TESTS -> false;
        };
    }
}
