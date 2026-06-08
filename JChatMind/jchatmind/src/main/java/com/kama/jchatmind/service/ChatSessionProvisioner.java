package com.kama.jchatmind.service;

import java.util.Map;

/**
 * 为子 Agent / 后台任务等场景幂等注册 chat_session，满足 chat_message.session_id 外键。
 */
public interface ChatSessionProvisioner {

    /**
     * 若 session 不存在则插入；已存在则跳过。
     *
     * @param sessionId  须为合法 UUID（与 chat_message 一致）
     * @param agentId    关联 Agent（须已存在于 agent 表）
     * @param title      会话标题
     * @param metadata   可选元数据（如 kind=hidden）
     */
    void ensureSession(String sessionId, String agentId, String title, Map<String, Object> metadata);
}
