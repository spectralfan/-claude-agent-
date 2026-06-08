package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingSessionBindingDTO;
import com.kama.jchatmind.coding.model.dto.CodingTaskDTO;
import com.kama.jchatmind.coding.model.dto.CreateCodingTaskRequest;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingSessionBindingService;
import com.kama.jchatmind.coding.service.CodingTaskAutoProvisioner;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
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

    @Override
    public CodingTask ensureActiveTask(String sessionId, String agentId) {
        CodingTask active = codingTaskService.getActiveTask(sessionId);
        if (active != null) {
            return active;
        }
        if (bindingService.findBinding(sessionId).isEmpty()) {
            return null;
        }
        CreateCodingTaskRequest request = buildCreateRequest(sessionId, agentId);
        String taskId = codingTaskService.createTask(request);
        CodingTaskDTO dto = codingTaskService.getTask(taskId);
        realtimeNotifier.tryPublish(sessionId, SseMessage.builder()
                .type(SseMessage.Type.CODING_STARTED)
                .payload(SseMessage.Payload.builder()
                        .taskId(taskId)
                        .workspace(dto.getWorkspaceRoot() + "/" + dto.getWorkspacePath())
                        .statusText("Coding 任务已自动创建")
                        .build())
                .build());
        log.info("已自动创建 Coding 任务 session={} taskId={}", sessionId, taskId);
        return codingTaskService.getTaskEntity(taskId);
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
