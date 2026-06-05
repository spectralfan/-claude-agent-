package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;

import java.util.List;

/**
 * 记忆重要性评估。
 */
public interface MemoryImportanceService {

    /**
     * 基于规则对记忆条目评分。
     * 工具调用密集 +3 / 含文件路径或代码 +2 / 含决策或确认 +5。
     */
    int evaluate(MemorySaveDTO dto);

    /**
     * 从内容中抽取标签（如 tool、code、decision）。
     */
    List<String> extractTags(MemorySaveDTO dto);
}
