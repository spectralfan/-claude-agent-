package com.kama.jchatmind.model.request;
import lombok.Data;

@Data
public class CreateChatSessionRequest {
    private String agentId;
    private String title;
    /** CHAT / CODING，不传则由后端自动检测 */
    private String type;
    private String workspaceRoot;
    private String workspacePath;
    private String approvalMode;
    private Boolean scaffoldOnCreate;
}