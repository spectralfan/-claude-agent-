package com.kama.jchatmind.coding.model.dto;

import lombok.Data;

@Data
public class CreateCodingTaskRequest {
    private String sessionId;
    private String agentId;
    /**
     * 用户在网页选择的工作区根（须在 coding.workspace.allowed-roots 白名单内）。
     * 为空则使用默认 coding.workspace.root。
     */
    private String workspaceRoot;
    /** 相对于 workspaceRoot 的子路径，如 "." 或 "module-a" */
    private String workspacePath;
    /** 技术栈 Profile id，如 java-maven / python-pytest */
    private String stackId;
    /** 选用的 Coding Skill id；为空则使用栈默认 skillId */
    private String skillId;
    /** strict | development | trusted，为空则用全局 default-mode */
    private String approvalMode;
    /** 工作区为空时从模板脚手架初始化 */
    private Boolean scaffoldOnCreate;
    /** 创建前根据 detectFiles 自动识别 stackId */
    private Boolean autoDetectStack;
}
