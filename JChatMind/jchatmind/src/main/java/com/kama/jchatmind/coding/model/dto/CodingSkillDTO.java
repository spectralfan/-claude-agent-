package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Coding Skill：可复用的任务级能力包（类似 Claude Code Skills）。
 */
@Data
@Builder
public class CodingSkillDTO {
    private String id;
    private String name;
    private String description;
    /** 注入 system prompt 的 Skill 指令 */
    private String prompt;
    /** 建议勾选的工具名（提示用户/Agent） */
    private List<String> suggestedTools;
}
