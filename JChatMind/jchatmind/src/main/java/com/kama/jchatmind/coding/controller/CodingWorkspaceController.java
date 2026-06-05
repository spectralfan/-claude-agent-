package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingWorkspaceOptionDTO;
import com.kama.jchatmind.coding.model.dto.WorkspaceDetectResultDTO;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.WorkspaceDetectService;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供网页选择本地 IDEA/Maven 工程工作区（白名单）。
 */
@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class CodingWorkspaceController {

    private final CodingWorkspaceService codingWorkspaceService;
    private final WorkspaceDetectService workspaceDetectService;

    @GetMapping("/workspaces")
    public ApiResponse<List<CodingWorkspaceOptionDTO>> listWorkspaces() {
        return ApiResponse.success(codingWorkspaceService.listWorkspaceOptions());
    }

    @GetMapping("/workspaces/detect")
    public ApiResponse<WorkspaceDetectResultDTO> detectWorkspace(
            @RequestParam String workspaceRoot,
            @RequestParam(defaultValue = ".") String workspacePath) {
        return ApiResponse.success(workspaceDetectService.detect(workspaceRoot, workspacePath));
    }
}
