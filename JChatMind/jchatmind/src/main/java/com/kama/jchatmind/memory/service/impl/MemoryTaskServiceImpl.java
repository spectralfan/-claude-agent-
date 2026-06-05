package com.kama.jchatmind.memory.service.impl;

import com.kama.jchatmind.memory.agent.MemoryAgent;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.mapper.MemoryTaskMapper;
import com.kama.jchatmind.memory.model.dto.MemoryConsolidationDTO;
import com.kama.jchatmind.memory.model.entity.MemoryTask;
import com.kama.jchatmind.memory.model.enums.MemoryTaskStatus;
import com.kama.jchatmind.memory.model.enums.MemoryTaskType;
import com.kama.jchatmind.memory.service.MemoryTaskService;
import com.kama.jchatmind.memory.support.MemorySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryTaskServiceImpl implements MemoryTaskService {

    private final MemoryTaskMapper memoryTaskMapper;
    private final MemoryAgent memoryAgent;
    private final MemoryProperties properties;
    private final MemorySupport support;

    @Override
    public String createConsolidationTask(MemoryConsolidationDTO dto) {
        MemoryTask task = MemoryTask.builder()
                .taskType(MemoryTaskType.CONSOLIDATION.getCode())
                .sessionId(dto.getSessionId())
                .status(MemoryTaskStatus.PENDING.getCode())
                .priority(5)
                .inputData(support.toJson(dto))
                .build();
        memoryTaskMapper.insert(task);
        log.info("已创建记忆整理任务 session={} taskId={}", dto.getSessionId(), task.getId());
        return task.getId();
    }

    @Override
    @Scheduled(fixedDelayString = "${memory.hub.task-poll-interval-ms:10000}")
    public void pollPendingTasks() {
        if (!properties.isEnabled() || !properties.isTaskPollingEnabled()) {
            return;
        }
        List<MemoryTask> pending = memoryTaskMapper.selectPending(properties.getTaskBatchSize());
        if (pending.isEmpty()) {
            return;
        }
        for (MemoryTask task : pending) {
            // 先同步置为 running，避免下个轮询周期重复分发
            int updated = memoryTaskMapper.updateStatus(
                    task.getId(),
                    MemoryTaskStatus.RUNNING.getCode(),
                    LocalDateTime.now(),
                    null, null, null);
            if (updated > 0) {
                memoryAgent.execute(task);
            }
        }
    }
}
