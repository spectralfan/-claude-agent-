package com.kama.jchatmind.session;

/**
 * Session 生命周期状态。
 */
public enum SessionState {
    CREATED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED
}