package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.OrchestrationProperties;
import com.kama.jchatmind.coding.mapper.OrchestrationTaskMapper;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.OrchestrationTask;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskStatus;
import com.kama.jchatmind.coding.orchestration.OrchestrationGraphChangedEvent;
import com.kama.jchatmind.coding.orchestration.OrchestrationTaskFinishedEvent;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import com.kama.jchatmind.coding.support.OrchestrationTaskConverter;
import com.kama.jchatmind.exception.BizException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OrchestrationTaskServiceImpl implements OrchestrationTaskService {

    private final OrchestrationTaskMapper orchestrationTaskMapper;
    private final OrchestrationTaskConverter converter;
    private final OrchestrationProperties orchestrationProperties;
    private final ApplicationEventPublisher eventPublisher;

    public OrchestrationTaskServiceImpl(
            OrchestrationTaskMapper orchestrationTaskMapper,
            OrchestrationTaskConverter converter,
            OrchestrationProperties orchestrationProperties,
            ApplicationEventPublisher eventPublisher) {
        this.orchestrationTaskMapper = orchestrationTaskMapper;
        this.converter = converter;
        this.orchestrationProperties = orchestrationProperties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrchestrationTaskDTO create(
            String parentSessionId,
            String parentCodingTaskId,
            OrchestrationTaskRole role,
            String title,
            String goal,
            String constraints,
            List<String> contextFiles,
            List<String> dependsOn,
            String workerAgentId,
            int depth,
            String spawnedFromTaskId,
            Map<String, Object> metadata) {
        if (depth > orchestrationProperties.getMaxDepth()) {
            throw new BizException("超过最大嵌套深度 " + orchestrationProperties.getMaxDepth());
        }
        List<String> deps = dependsOn != null ? new ArrayList<>(dependsOn) : new ArrayList<>();
        validateDependencies(parentSessionId, deps, null);

        OrchestrationTaskStatus initial = resolveInitialStatus(parentSessionId, deps);
        OrchestrationTask entity = OrchestrationTask.builder()
                .parentSessionId(parentSessionId)
                .parentCodingTaskId(parentCodingTaskId)
                .role(role.getCode())
                .title(title != null && !title.isBlank() ? title.trim() : "任务")
                .goal(goal)
                .constraints(constraints)
                .contextFiles(converter.toJsonList(contextFiles))
                .dependsOn(converter.toJsonList(deps))
                .status(initial.getCode())
                .depth(depth)
                .spawnedFromTaskId(spawnedFromTaskId)
                .workerAgentId(workerAgentId)
                .metadata(converter.toJsonMetadata(metadata))
                .createdAt(LocalDateTime.now())
                .build();
        orchestrationTaskMapper.insert(entity);
        OrchestrationTaskDTO dto = converter.toDto(orchestrationTaskMapper.selectById(entity.getId()));
        publishGraphChanged(parentSessionId);
        return dto;
    }

    @Override
    public Optional<OrchestrationTaskDTO> findById(String taskId) {
        return Optional.ofNullable(orchestrationTaskMapper.selectById(taskId))
                .map(converter::toDto);
    }

    @Override
    public List<OrchestrationTaskDTO> listByParentSession(String parentSessionId) {
        return orchestrationTaskMapper.selectByParentSession(parentSessionId).stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public List<OrchestrationTaskDTO> listReadyByParentSession(String parentSessionId) {
        return orchestrationTaskMapper.selectByParentSessionAndStatus(
                        parentSessionId, OrchestrationTaskStatus.READY.getCode()).stream()
                .map(converter::toDto)
                .toList();
    }

    @Override
    public int countRunning(String parentSessionId) {
        return orchestrationTaskMapper.countRunningByParentSession(parentSessionId);
    }

    @Override
    public OrchestrationTaskDTO update(
            String taskId,
            String status,
            String goal,
            String constraints,
            List<String> dependsOn) {
        OrchestrationTask existing = orchestrationTaskMapper.selectById(taskId);
        if (existing == null) {
            throw new BizException("任务不存在: " + taskId);
        }
        OrchestrationTaskStatus current = OrchestrationTaskStatus.fromCode(existing.getStatus());
        if (current != OrchestrationTaskStatus.PENDING && current != OrchestrationTaskStatus.READY) {
            throw new BizException("仅 PENDING/READY 任务可更新");
        }
        List<String> deps = dependsOn != null ? dependsOn : converter.toDto(existing).getDependsOn();
        validateDependencies(existing.getParentSessionId(), deps, taskId);

        String newStatus = status;
        if (newStatus == null || newStatus.isBlank()) {
            newStatus = resolveInitialStatus(existing.getParentSessionId(), deps).getCode();
        }
        OrchestrationTask updated = OrchestrationTask.builder()
                .id(existing.getId())
                .parentSessionId(existing.getParentSessionId())
                .parentCodingTaskId(existing.getParentCodingTaskId())
                .role(existing.getRole())
                .title(existing.getTitle())
                .goal(goal != null ? goal : existing.getGoal())
                .constraints(constraints != null ? constraints : existing.getConstraints())
                .contextFiles(existing.getContextFiles())
                .dependsOn(converter.toJsonList(deps))
                .status(newStatus)
                .depth(existing.getDepth())
                .spawnedFromTaskId(existing.getSpawnedFromTaskId())
                .workerAgentId(existing.getWorkerAgentId())
                .resultSummary(existing.getResultSummary())
                .errorMessage(existing.getErrorMessage())
                .metadata(existing.getMetadata())
                .createdAt(existing.getCreatedAt())
                .startedAt(existing.getStartedAt())
                .finishedAt(existing.getFinishedAt())
                .build();
        orchestrationTaskMapper.updateStatus(updated);
        publishGraphChanged(existing.getParentSessionId());
        return converter.toDto(orchestrationTaskMapper.selectById(taskId));
    }

    @Override
    public void markRunning(String taskId) {
        OrchestrationTask existing = requireTask(taskId);
        OrchestrationTask updated = copyWithStatus(existing, OrchestrationTaskStatus.RUNNING);
        updated.setStartedAt(LocalDateTime.now());
        orchestrationTaskMapper.updateStatus(updated);
    }

    @Override
    public void markCompleted(String taskId, String summary) {
        OrchestrationTask existing = requireTask(taskId);
        OrchestrationTask updated = copyWithStatus(existing, OrchestrationTaskStatus.COMPLETED);
        updated.setResultSummary(summary);
        updated.setFinishedAt(LocalDateTime.now());
        orchestrationTaskMapper.updateStatus(updated);
        refreshDependents(existing.getParentSessionId());
        publishTaskFinished(converter.toDto(updated));
    }

    @Override
    public void markFailed(String taskId, String errorMessage) {
        OrchestrationTask existing = requireTask(taskId);
        OrchestrationTask updated = copyWithStatus(existing, OrchestrationTaskStatus.FAILED);
        updated.setErrorMessage(errorMessage);
        updated.setFinishedAt(LocalDateTime.now());
        orchestrationTaskMapper.updateStatus(updated);
        refreshDependents(existing.getParentSessionId());
        publishTaskFinished(converter.toDto(updated));
    }

    @Override
    public void refreshDependents(String parentSessionId) {
        List<OrchestrationTask> all = orchestrationTaskMapper.selectByParentSession(parentSessionId);
        Map<String, OrchestrationTask> byId = new HashMap<>();
        for (OrchestrationTask t : all) {
            byId.put(t.getId(), t);
        }
        boolean changed = false;
        for (OrchestrationTask task : all) {
            if (!OrchestrationTaskStatus.PENDING.getCode().equals(task.getStatus())) {
                continue;
            }
            List<String> deps = converter.toDto(task).getDependsOn();
            if (deps.isEmpty() || allDependenciesCompleted(deps, byId)) {
                OrchestrationTask updated = copyWithStatus(task, OrchestrationTaskStatus.READY);
                orchestrationTaskMapper.updateStatus(updated);
                changed = true;
            }
        }
        if (changed) {
            publishGraphChanged(parentSessionId);
        }
    }

    private void publishGraphChanged(String parentSessionId) {
        if (parentSessionId != null) {
            eventPublisher.publishEvent(new OrchestrationGraphChangedEvent(parentSessionId));
        }
    }

    private void publishTaskFinished(OrchestrationTaskDTO task) {
        if (task != null) {
            eventPublisher.publishEvent(new OrchestrationTaskFinishedEvent(task));
        }
    }

    @Override
    public boolean allTerminal(String parentSessionId) {
        List<OrchestrationTask> all = orchestrationTaskMapper.selectByParentSession(parentSessionId);
        if (all.isEmpty()) {
            return false;
        }
        return all.stream().allMatch(t ->
                OrchestrationTaskStatus.fromCode(t.getStatus()).isTerminal());
    }

    @Override
    public boolean hasRunning(String parentSessionId) {
        return countRunning(parentSessionId) > 0;
    }

    private OrchestrationTaskStatus resolveInitialStatus(String parentSessionId, List<String> deps) {
        if (deps == null || deps.isEmpty()) {
            return OrchestrationTaskStatus.READY;
        }
        Map<String, OrchestrationTask> byId = new HashMap<>();
        for (OrchestrationTask t : orchestrationTaskMapper.selectByParentSession(parentSessionId)) {
            byId.put(t.getId(), t);
        }
        return allDependenciesCompleted(deps, byId)
                ? OrchestrationTaskStatus.READY
                : OrchestrationTaskStatus.PENDING;
    }

    private boolean allDependenciesCompleted(List<String> deps, Map<String, OrchestrationTask> byId) {
        for (String depId : deps) {
            OrchestrationTask dep = byId.get(depId);
            if (dep == null || !OrchestrationTaskStatus.COMPLETED.getCode().equals(dep.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private void validateDependencies(String parentSessionId, List<String> deps, String selfId) {
        if (deps == null || deps.isEmpty()) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (String depId : deps) {
            if (selfId != null && selfId.equals(depId)) {
                throw new BizException("任务不能依赖自身");
            }
            OrchestrationTask dep = orchestrationTaskMapper.selectById(depId);
            if (dep == null || !parentSessionId.equals(dep.getParentSessionId())) {
                throw new BizException("无效依赖任务: " + depId);
            }
            if (hasCycle(depId, selfId, visited)) {
                throw new BizException("检测到循环依赖");
            }
        }
    }

    private boolean hasCycle(String taskId, String addingId, Set<String> visited) {
        if (!visited.add(taskId)) {
            return true;
        }
        OrchestrationTask task = orchestrationTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        for (String dep : converter.toDto(task).getDependsOn()) {
            if (addingId != null && addingId.equals(dep)) {
                return true;
            }
            if (hasCycle(dep, addingId, visited)) {
                return true;
            }
        }
        return false;
    }

    private OrchestrationTask requireTask(String taskId) {
        OrchestrationTask existing = orchestrationTaskMapper.selectById(taskId);
        if (existing == null) {
            throw new BizException("任务不存在: " + taskId);
        }
        return existing;
    }

    private OrchestrationTask copyWithStatus(OrchestrationTask existing, OrchestrationTaskStatus status) {
        return OrchestrationTask.builder()
                .id(existing.getId())
                .parentSessionId(existing.getParentSessionId())
                .parentCodingTaskId(existing.getParentCodingTaskId())
                .role(existing.getRole())
                .title(existing.getTitle())
                .goal(existing.getGoal())
                .constraints(existing.getConstraints())
                .contextFiles(existing.getContextFiles())
                .dependsOn(existing.getDependsOn())
                .status(status.getCode())
                .depth(existing.getDepth())
                .spawnedFromTaskId(existing.getSpawnedFromTaskId())
                .workerAgentId(existing.getWorkerAgentId())
                .resultSummary(existing.getResultSummary())
                .errorMessage(existing.getErrorMessage())
                .metadata(existing.getMetadata())
                .createdAt(existing.getCreatedAt())
                .startedAt(existing.getStartedAt())
                .finishedAt(existing.getFinishedAt())
                .build();
    }
}
