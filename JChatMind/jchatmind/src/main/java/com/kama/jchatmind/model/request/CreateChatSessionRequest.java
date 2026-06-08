package com.kama.jchatmind.model.request;

import lombok.Data;

@Data
public class CreateChatSessionRequest {
    private String agentId;
    private String title;
    /** Coding 工作区绑定：首条用户消息时自动创建任务 */
    private String workspaceRoot;
    private String workspacePath;
    private String approvalMode;
    private Boolean scaffoldOnCreate;
}
