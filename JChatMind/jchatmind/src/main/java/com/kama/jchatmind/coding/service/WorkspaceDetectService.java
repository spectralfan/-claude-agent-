package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.WorkspaceDetectResultDTO;

public interface WorkspaceDetectService {

    WorkspaceDetectResultDTO detect(String workspaceRoot, String workspacePath);
}
