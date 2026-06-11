package com.kama.jchatmind.coding;

import java.util.List;

/**
 * 从子 Agent 最后一条 assistant 消息中提取结构化交付/审查摘要。
 */
public final class OrchestrationSummaryExtractor {

    private static final List<String> STRUCTURED_MARKERS = List.of("## 交付摘要", "## 审查结论");

    private OrchestrationSummaryExtractor() {
    }

    public static String extractStructuredSummary(String fullAssistantContent) {
        if (fullAssistantContent == null || fullAssistantContent.isBlank()) {
            return fullAssistantContent;
        }
        String trimmed = fullAssistantContent.trim();
        for (String marker : STRUCTURED_MARKERS) {
            int idx = trimmed.indexOf(marker);
            if (idx >= 0) {
                return trimmed.substring(idx).trim();
            }
        }
        return trimmed;
    }

    public static boolean isReviewFailed(String summary) {
        if (summary == null || summary.isBlank()) {
            return false;
        }
        String normalized = summary.replace(" ", "").replace("：", ":");
        return normalized.contains("VERDICT:FAIL");
    }
}
