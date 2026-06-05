package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.service.MemoryImportanceService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于规则的重要性评估实现。
 */
@Service
public class MemoryImportanceServiceImpl implements MemoryImportanceService {

    private static final int SCORE_TOOL = 3;
    private static final int SCORE_CODE = 2;
    private static final int SCORE_DECISION = 5;
    private static final int MAX_SCORE = 10;

    /** 文件路径或代码片段特征 */
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(```)|(\\b\\w+\\.(java|py|ts|tsx|js|sql|xml|yaml|yml|json|md)\\b)|([A-Za-z]:\\\\)|(/\\w+/\\w+)");

    /** 决策/确认类关键词 */
    private static final String[] DECISION_KEYWORDS = {
            "确认", "决定", "同意", "批准", "通过", "方案", "确定使用",
            "confirm", "approve", "decision", "agreed", "let's go", "proceed"
    };

    @Override
    public int evaluate(MemorySaveDTO dto) {
        int score = 0;
        if (dto == null) {
            return score;
        }

        if (!CollectionUtils.isEmpty(dto.getToolCalls())) {
            score += SCORE_TOOL;
        }

        String content = dto.getContent();
        if (StringUtils.hasText(content)) {
            if (CODE_PATTERN.matcher(content).find()) {
                score += SCORE_CODE;
            }
            String lower = content.toLowerCase();
            for (String keyword : DECISION_KEYWORDS) {
                if (content.contains(keyword) || lower.contains(keyword.toLowerCase())) {
                    score += SCORE_DECISION;
                    break;
                }
            }
        }

        return Math.min(score, MAX_SCORE);
    }

    @Override
    public List<String> extractTags(MemorySaveDTO dto) {
        List<String> tags = new ArrayList<>();
        if (dto == null) {
            return tags;
        }
        if (!CollectionUtils.isEmpty(dto.getToolCalls())) {
            tags.add("tool");
        }
        String content = dto.getContent();
        if (StringUtils.hasText(content)) {
            if (CODE_PATTERN.matcher(content).find()) {
                tags.add("code");
            }
            String lower = content.toLowerCase();
            for (String keyword : DECISION_KEYWORDS) {
                if (content.contains(keyword) || lower.contains(keyword.toLowerCase())) {
                    tags.add("decision");
                    break;
                }
            }
        }
        return tags;
    }
}
