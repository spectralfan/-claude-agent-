package com.kama.jchatmind.coding.orchestration;

/**
 * 编排任务图变更（创建/更新/依赖解锁），通知 Dispatcher 扫描 READY 队列。
 */
public record OrchestrationGraphChangedEvent(String parentSessionId) {
}
