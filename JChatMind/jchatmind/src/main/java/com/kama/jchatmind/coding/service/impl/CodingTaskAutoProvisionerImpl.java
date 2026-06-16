package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.mapper.CodingTaskMapper;
import com.kama.jchatmind.coding.model.dto.CodingSessionBindingDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskDTO;
import com.kama.jchatmind.coding.model.dto.CreateCodingTaskRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingSessionBindingService;
import com.kama.jchatmind.coding.service.CodingTaskAutoProvisioner;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import java.time.LocalDateTime;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingTaskAutoProvisionerImpl implements CodingTaskAutoProvisioner {

    private final CodingTaskService codingTaskService;
    private final CodingSessionBindingService bindingService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingProperties codingProperties;
    private final RealtimeNotifier realtimeNotifier;
    private final CodingTaskMapper codingTaskMapper;

    @Override
    public CodingTask ensureActiveTask(String sessionId, String agentId) {
        CodingTask active = codingTaskService.getActiveTask(sessionId);
        CodingSessionBindingDTO binding = bindingService.findBinding(sessionId).orElse(null);
        String currentRoot = (binding != null && StringUtils.hasText(binding.getWorkspaceRoot()))
                ? binding.getWorkspaceRoot()
                : codingWorkspaceService.getWorkspaceRoot().toString();
        String currentPath = (binding != null && StringUtils.hasText(binding.getWorkspacePath()))
                ? binding.getWorkspacePath()
                : ".";
        if (active != null) {
            boolean workspaceChanged = !currentRoot.equals(active.getWorkspaceRoot())
                    || !currentPath.equals(active.getWorkspacePath());
            if (workspaceChanged) {
                log.info("Workspace 已变更 session={}, 废弃旧任务 taskId={} oldRoot={} -> newRoot={}",
                        sessionId, active.getId(),
                        active.getWorkspaceRoot() + "/" + active.getWorkspacePath(),
                        currentRoot + "/" + currentPath);
                codingTaskMapper.updateStatus(active.getId(), "completed",
                        LocalDateTime.now(), "workspace 变更，任务已迁移", null);
                cleanupDotTasks(active);
                active = null;
            }
        }
        if (active != null) {
            if (isTerminalStatus(active.getStatus())) {
                log.info("复用已完成任务 session={} taskId={}, 重置为 pending", sessionId, active.getId());
                codingTaskMapper.updateStatus(active.getId(), "pending", null, null, null);
                cleanupDotTasks(active);
                return codingTaskService.getTaskEntity(active.getId());
            }
            log.info("复用活动任务 session={} taskId={} workspace={}",
                    sessionId, active.getId(), active.getWorkspaceRoot());
            return active;
        }
        if (bindingService.findBinding(sessionId).isEmpty()) {
            log.info("No binding for session={}, using default", sessionId);
        }
        CreateCodingTaskRequest request = buildCreateRequest(sessionId, agentId);
        String taskId = codingTaskService.createTask(request);
        CodingTaskDTO dto = codingTaskService.getTask(taskId);
        String statusText = dto.getStackId() != null
                ? "Coding 任务已创建, 技术栈: " + dto.getStackId()
                : "Coding 任务已自动创建";
        realtimeNotifier.tryPublish(sessionId, SseMessage.builder()
                .type(SseMessage.Type.CODING_STARTED)
                .payload(SseMessage.Payload.builder()
                        .taskId(taskId)
                        .stackId(dto.getStackId())
                        .workspace(dto.getWorkspaceRoot() + "/" + dto.getWorkspacePath())
                        .statusText(statusText)
                        .build())
                .build());
        log.info("已自动创建 Coding 任务 session={} taskId={}", sessionId, taskId);
        return codingTaskService.getTaskEntity(taskId);
    }

    private boolean isTerminalStatus(String status) {
        return "completed".equals(status) || "failed".equals(status)
                || "timeout".equals(status) || "rejected".equals(status);
    }

    private void cleanupDotTasks(CodingTask task) {
        try {
            java.nio.file.Path workspace = codingWorkspaceService.resolveForTask(task);
            java.nio.file.Path tasksDir = workspace.resolve(".tasks");
            if (java.nio.file.Files.exists(tasksDir)) {
                java.nio.file.Files.walk(tasksDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
                log.info("已清理 .tasks: {}", tasksDir);
            }
        } catch (Exception e) {
            log.warn("清理 .tasks 失败: {}", e.getMessage());
        }
    }

    private CreateCodingTaskRequest buildCreateRequest(String sessionId, String agentId) {
        CreateCodingTaskRequest request = new CreateCodingTaskRequest();
        request.setSessionId(sessionId);
        request.setAgentId(agentId);
        request.setAutoDetectStack(true);

        CodingSessionBindingDTO binding = bindingService.findBinding(sessionId).orElse(null);
        if (binding != null && StringUtils.hasText(binding.getWorkspaceRoot())) {
            request.setWorkspaceRoot(binding.getWorkspaceRoot());
            request.setWorkspacePath(
                    StringUtils.hasText(binding.getWorkspacePath()) ? binding.getWorkspacePath() : ".");
            request.setApprovalMode(binding.getApprovalMode());
            request.setScaffoldOnCreate(binding.getScaffoldOnCreate());
        } else {
            request.setWorkspaceRoot(codingWorkspaceService.getWorkspaceRoot().toString());
            request.setWorkspacePath(".");
            if (codingProperties.getApproval().getDefaultMode() != null) {
                request.setApprovalMode(codingProperties.getApproval().getDefaultMode().getCode());
            }
        }
        return request;
    }
}