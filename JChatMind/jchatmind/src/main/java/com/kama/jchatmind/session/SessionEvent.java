package com.kama.jchatmind.session;

import org.springframework.context.ApplicationEvent;

/**
 * Session 生命周期事件基类。
 */
public abstract class SessionEvent extends ApplicationEvent {

    private final String sessionId;
    private final String runId;

    public SessionEvent(Object source, String sessionId, String runId) {
        super(source);
        this.sessionId = sessionId;
        this.runId = runId;
    }

    public String getSessionId() { return sessionId; }
    public String getRunId() { return runId; }

    /** Session 创建事件 */
    public static class Created extends SessionEvent {
        public Created(Object source, String sessionId) {
            super(source, sessionId, null);
        }
    }

    /** Run 开始事件 */
    public static class RunStarted extends SessionEvent {
        private final String goal;
        public RunStarted(Object source, String sessionId, String runId, String goal) {
            super(source, sessionId, runId);
            this.goal = goal;
        }
        public String getGoal() { return goal; }
    }

    /** Run 结束事件 */
    public static class RunFinished extends SessionEvent {
        private final String status;
        private final String reason;
        public RunFinished(Object source, String sessionId, String runId, String status, String reason) {
            super(source, sessionId, runId);
            this.status = status;
            this.reason = reason;
        }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
    }

    /** Session 状态变更事件 */
    public static class StateChanged extends SessionEvent {
        private final SessionState oldState;
        private final SessionState newState;
        public StateChanged(Object source, String sessionId, SessionState oldState, SessionState newState) {
            super(source, sessionId, null);
            this.oldState = oldState;
            this.newState = newState;
        }
        public SessionState getOldState() { return oldState; }
        public SessionState getNewState() { return newState; }
    }
}