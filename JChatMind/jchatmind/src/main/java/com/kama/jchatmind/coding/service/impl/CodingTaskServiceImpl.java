package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.mapper.CodingTaskMapper;
import com.kama.jchatmind.coding.model.dto.CodingStackDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskDTO;
import com.kama.jchatmind.coding.model.dto.CreateCodingTaskRequest;
import com.kama.jchatmind.coding.model.dto.CodingTaskMetadata;
import com.kama.jchatmind.coding.model.dto.WorkspaceDetectResultDTO;
import com.kama.jchatmind.coding.model.enums.CodingApprovalMode;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.CodingTaskStatus;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.WorkspaceDetectService;
import com.kama.jchatmind.coding.service.WorkspaceScaffoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodingTaskServiceImpl implements CodingTaskService {

    private final CodingTaskMapper codingTaskMapper;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingStackService codingStackService;
    private final WorkspaceDetectService workspaceDetectService;
    private final WorkspaceScaffoldService workspaceScaffoldService;
    private final CodingProperties codingProperties;

    @Override
    public String createTask(CreateCodingTaskRequest request) {
        CodingApprovalMode approvalMode = null;
        if (request.getApprovalMode() != null && !request.getApprovalMode().isBlank()) {
            approvalMode = CodingApprovalMode.fromCode(request.getApprovalMode());
        }

        String stackId = request.getStackId();
        String language = null;
        String skillId = request.getSkillId();

        boolean autoDetect = request.getAutoDetectStack() != null
                ? Boolean.TRUE.equals(request.getAutoDetectStack())
                : codingProperties.getStack().isAutoDetect();
        if (autoDetect && (stackId == null || stackId.isBlank())) {
            WorkspaceDetectResultDTO detected = workspaceDetectService.detect(
                    request.getWorkspaceRoot(), request.getWorkspacePath());
            if (detected.getStackId() != null) {
                stackId = detected.getStackId();
                language = detected.getLanguage();
            }
        }

        if (stackId != null && !stackId.isBlank()) {
            final String lookupStackId = stackId;
            CodingStackDTO stack = codingStackService.findById(lookupStackId)
                    .orElseThrow(() -> new IllegalArgumentException("未知技术栈: " + lookupStackId));
            if (language == null) {
                language = stack.getLanguage();
            }
            if (skillId == null || skillId.isBlank()) {
                skillId = stack.getSkillId();
            }
        }

        CodingTaskMetadata metadata = CodingTaskMetadata.builder()
                .skillId(skillId)
                .stackId(stackId)
                .language(language)
                .approvalMode(approvalMode)
                .build();

        String taskId = createTaskInternal(
                request.getSessionId(),
                request.getAgentId(),
                request.getWorkspaceRoot(),
                request.getWorkspacePath(),
                metadata.toJson()
        );

        if (Boolean.TRUE.equals(request.getScaffoldOnCreate()) && stackId != null) {
            CodingTask task = getTaskEntity(taskId);
            workspaceScaffoldService.scaffoldIfNeeded(task, stackId, true);
        }

        return taskId;
    }

    @Override
    public String createTask(String sessionId, String agentId, String workspaceRoot, String workspacePath) {
        return createTaskInternal(sessionId, agentId, workspaceRoot, workspacePath, null);
    }

    private String createTaskInternal(String sessionId, String agentId,
                                      String workspaceRoot, String workspacePath,
                                      String metadataJson) {
        CodingTask active = getActiveTask(sessionId);
        if (active != null) {
            throw new IllegalStateException("当前会话已有进行中的 coding 任务: " + active.getId());
        }
        Path resolvedRoot = codingWorkspaceService.resolveAllowedRoot(workspaceRoot);
        String subPath = (workspacePath == null || workspacePath.isBlank()) ? "." : workspacePath.trim();
        CodingTask task = CodingTask.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .status(CodingTaskStatus.PENDING.getCode())
                .workspaceRoot(resolvedRoot.toString())
                .workspacePath(subPath)
                .metadata(metadataJson)
                .build();
        codingTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public CodingTaskDTO getTask(String taskId) {
        CodingTask task = getTaskEntity(taskId);
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        return CodingTaskDTO.builder()
                .id(task.getId())
                .sessionId(task.getSessionId())
                .agentId(task.getAgentId())
                .status(task.getStatus())
                .workspacePath(task.getWorkspacePath())
                .workspaceRoot(task.getWorkspaceRoot())
                .command(task.getCommand())
                .resultSummary(task.getResultSummary())
                .approvalReason(task.getApprovalReason())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .skillId(metadata.getSkillId())
                .stackId(metadata.getStackId())
                .language(metadata.getLanguage())
                .approvalMode(metadata.getApprovalMode() != null
                        ? metadata.getApprovalMode().getCode() : null)
                .build();
    }

    @Override
    public CodingTask getTaskEntity(String taskId) {
        CodingTask task = codingTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    @Override
    public CodingTask getActiveTask(String sessionId) {
        return codingTaskMapper.selectActiveBySession(sessionId);
    }

    @Override
    public boolean applyDetectedStackIfAbsent(CodingTask task) {
        if (task == null || task.getId() == null) {
            return false;
        }
        if (!codingProperties.getStack().isAutoDetect()) {
            return false;
        }
        CodingTaskMetadata metadata = CodingTaskMetadata.fromJson(task.getMetadata());
        if (StringUtils.hasText(metadata.getStackId())) {
            return false;
        }
        WorkspaceDetectResultDTO detected = workspaceDetectService.detect(
                task.getWorkspaceRoot(), task.getWorkspacePath());
        if (detected.getStackId() == null || detected.getStackId().isBlank()) {
            return false;
        }
        String stackId = detected.getStackId();
        CodingStackDTO stack = codingStackService.findById(stackId).orElse(null);
        metadata.setStackId(stackId);
        if (detected.getLanguage() != null) {
            metadata.setLanguage(detected.getLanguage());
        } else if (stack != null) {
            metadata.setLanguage(stack.getLanguage());
        }
        if (!StringUtils.hasText(metadata.getSkillId()) && stack != null && StringUtils.hasText(stack.getSkillId())) {
            metadata.setSkillId(stack.getSkillId());
        }
        codingTaskMapper.updateMetadata(task.getId(), metadata.toJson());
        task.setMetadata(metadata.toJson());
        log.info("Coding 任务 {} 已自动识别技术栈: {}", task.getId(), stackId);
        return true;
    }

    @Override
    public void markRunning(String taskId) {
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.RUNNING.getCode(), null, null, null);
    }

    @Override
    public void markWaitingApproval(String taskId, String command, String pendingAction, String pendingPayload) {
        codingTaskMapper.updateCommand(taskId, command, pendingAction, pendingPayload);
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.WAITING_APPROVAL.getCode(), null, null, null);
    }

    @Override
    public void completeTask(String taskId, String summary) {
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.COMPLETED.getCode(), LocalDateTime.now(), summary, null);
    }

    @Override
    public void failTask(String taskId, String reason) {
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.FAILED.getCode(), LocalDateTime.now(), reason, null);
    }

    @Override
    public void timeoutTask(String taskId) {
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.TIMEOUT.getCode(), LocalDateTime.now(), "命令执行超时", null);
    }

    @Override
    public void rejectTask(String taskId, String reason) {
        codingTaskMapper.updateStatus(taskId, CodingTaskStatus.REJECTED.getCode(), LocalDateTime.now(), null, reason);
        codingTaskMapper.clearPending(taskId);
    }

    @Override
    public void clearPending(String taskId) {
        codingTaskMapper.clearPending(taskId);
    }

    @Override
    public void recordExecutionResult(String taskId, String command, String resultSummary) {
        codingTaskMapper.recordExecutionResult(taskId, command, resultSummary);
    }
}
