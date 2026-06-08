package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 保存在 chat_session.metadata 中的 Coding 工作区绑定（首条消息时自动建任务）。
 */
@Data
@Builder
public class CodingSessionBindingDTO {
    private String workspaceRoot;
    private String workspacePath;
    private String approvalMode;
    private Boolean scaffoldOnCreate;
}
