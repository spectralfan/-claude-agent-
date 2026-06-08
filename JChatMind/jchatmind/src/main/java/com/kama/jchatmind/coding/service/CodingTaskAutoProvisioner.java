package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.entity.CodingTask;

/**
 * 会话首条消息或编排继续时，按 session 绑定自动创建/确保 Coding 任务存在。
 */
public interface CodingTaskAutoProvisioner {

    /**
     * @return 当前活动任务；若无则按 session.metadata.coding 或默认工作区创建
     */
    CodingTask ensureActiveTask(String sessionId, String agentId);
}
