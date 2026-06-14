package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.*;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingApprovalService;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingCommandService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/coding/tasks")
@RequiredArgsConstructor
public class CodingController {

    private final CodingTaskService codingTaskService;
    private final CodingApprovalService codingApprovalService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final RealtimeNotifier realtimeNotifier;
    private final CodingCommandService codingCommandService;

    @PostMapping
    public ApiResponse<CodingTaskDTO> createTask(@RequestBody CreateCodingTaskRequest request) {
        String id = codingTaskService.createTask(request);
        CodingTaskDTO task = codingTaskService.getTask(id);
        realtimeNotifier.tryPublish(request.getSessionId(), SseMessage.builder()
                .type(SseMessage.Type.CODING_STARTED)
                .payload(SseMessage.Payload.builder()
                        .taskId(id)
                        .workspace(task.getWorkspaceRoot() + "/" + task.getWorkspacePath())
                        .statusText("Coding 任务已创建")
                        .build())
                .build());
        if (Boolean.TRUE.equals(request.getScaffoldOnCreate())) {
            realtimeNotifier.tryPublish(request.getSessionId(), SseMessage.builder()
                    .type(SseMessage.Type.CODING_SCAFFOLD_DONE)
                    .payload(SseMessage.Payload.builder()
                            .taskId(id)
                            .statusText("脚手架已初始化")
                            .detail(task.getStackId())
                            .done(true)
                            .build())
                    .build());
        }
        return ApiResponse.success(task);
    }

    @PostMapping("/{taskId}/run-shell")
    public ApiResponse<CommandExecutionResult> runShell(
            @PathVariable String taskId, @RequestBody RunShellApiRequest request) {
        if (request.getCommand() == null || request.getCommand().isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        CommandExecutionResult result = codingCommandService.executeShell(taskId, request.getCommand().trim());
        realtimeNotifier.tryPublish(
                request.getSessionId() != null ? request.getSessionId() : task.getSessionId(),
                SseMessage.builder()
                        .type(SseMessage.Type.CODING_COMMAND_OUTPUT)
                        .payload(SseMessage.Payload.builder()
                                .taskId(taskId)
                                .command(request.getCommand().trim())
                                .exitCode(result.getExitCode())
                                .output(result.getOutput())
                                .done(result.getExitCode() == 0 && !result.isTimeout())
                                .build())
                        .build());
        return ApiResponse.success(result);
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<CommandExecutionResult> approve(@PathVariable String taskId) {
        CommandExecutionResult result = codingApprovalService.approve(taskId);
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        realtimeNotifier.tryPublish(task.getSessionId(), SseMessage.builder()
                .type(SseMessage.Type.CODING_COMMAND_OUTPUT)
                .payload(SseMessage.Payload.builder()
                        .taskId(taskId)
                        .command(task.getCommand())
                        .exitCode(result.getExitCode())
                        .output(result.getOutput())
                        .done(!result.isTimeout() && result.getExitCode() == 0)
                        .build())
                .build());
        return ApiResponse.success(result);
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<Void> reject(@PathVariable String taskId, @RequestBody RejectCodingTaskRequest request) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        codingApprovalService.reject(taskId, request.getReason());
        realtimeNotifier.tryPublish(task.getSessionId(), SseMessage.builder()
                .type(SseMessage.Type.CODING_FAILED)
                .payload(SseMessage.Payload.builder()
                        .taskId(taskId)
                        .statusText("审批被拒绝")
                        .detail(request.getReason())
                        .done(true)
                        .build())
                .build());
        return ApiResponse.success();
    }

    @GetMapping("/session/{sessionId}/active")
    public ApiResponse<CodingTaskDTO> getActiveTask(@PathVariable String sessionId) {
        CodingTask task = codingTaskService.getActiveTask(sessionId);
        if (task == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(codingTaskService.getTask(task.getId()));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<CodingTaskDTO> getTask(@PathVariable String taskId) {
        return ApiResponse.success(codingTaskService.getTask(taskId));
    }

    @GetMapping("/{taskId}/tree")
    public ApiResponse<List<FileNode>> getTaskTree(
            @PathVariable String taskId,
            @RequestParam(defaultValue = ".") String path) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        return ApiResponse.success(codingWorkspaceService.listDirectoryForTask(task, path));
    }

    @GetMapping("/{taskId}/file")
    public ApiResponse<CodingFileContentDTO> getTaskFile(
            @PathVariable String taskId,
            @RequestParam String path) {
        CodingTask task = codingTaskService.getTaskEntity(taskId);
        return ApiResponse.success(codingWorkspaceService.readFileForTask(task, path));
    }
}
