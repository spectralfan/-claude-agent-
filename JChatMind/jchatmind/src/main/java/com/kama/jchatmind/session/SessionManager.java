package com.kama.jchatmind.session;

import com.kama.jchatmind.session.store.NoteStore;
import com.kama.jchatmind.session.store.ThreadStore;
import com.kama.jchatmind.session.store.MetaStore;

/**
 * SessionManager — 统一会话生命周期管理。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>状态管理：CREATED → ACTIVE → PAUSED → COMPLETED / FAILED</li>
 *   <li>并发控制：数据库乐观锁（version 字段）</li>
 *   <li>事件发布：SessionCreatedEvent / RunStartedEvent / RunFinishedEvent</li>
 *   <li>存储协调：ThreadStore + NoteStore + MetaStore 统一管理</li>
 * </ul>
 */
public interface SessionManager {

    // ==================== 生命周期 ====================

    /**
     * 创建新 session。
     * @return 新 session 的 ID
     */
    String createSession(String agentId, String title);

    /** 激活 session（从 CREATED 或 PAUSED 转为 ACTIVE） */
    void activateSession(String sessionId);

    /** 暂停 session */
    void pauseSession(String sessionId);

    /** 完成 session */
    void completeSession(String sessionId);

    /** 标记 session 失败 */
    void failSession(String sessionId, String reason);

    // ==================== Run 管理 ====================

    /**
     * 开始一次新的 run。
     * @return 新生成的 runId
     */
    String startRun(String sessionId, String goal);

    /** 结束一次 run */
    void finishRun(String sessionId, String runId, String status, String reason);

    // ==================== 存储访问 ====================

    ThreadStore getThreadStore();
    NoteStore getNoteStore();
    MetaStore getMetaStore();

    // ==================== 状态查询 ====================

    SessionMeta getSession(String sessionId);
    boolean isActive(String sessionId);
}