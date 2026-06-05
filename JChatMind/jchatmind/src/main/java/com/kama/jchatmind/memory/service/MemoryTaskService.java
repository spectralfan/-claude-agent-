package com.kama.jchatmind.memory.service;

import com.kama.jchatmind.memory.model.dto.MemoryConsolidationDTO;

/**
 * 记忆异步任务调度。任务以数据库持久化方式排队，定时轮询执行，支持重启恢复。
 */
public interface MemoryTaskService {

    /**
     * 创建一个记忆整理任务（pending），返回任务 ID。
     */
    String createConsolidationTask(MemoryConsolidationDTO dto);

    /**
     * 轮询并分发待处理任务（由定时器调用，亦可手动触发）。
     */
    void pollPendingTasks();
}
