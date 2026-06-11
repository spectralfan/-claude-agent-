package com.kama.jchatmind.coding.context;

/**
 * Scheduler 单次 {@code JChatMind.run()} 内的轮询计数，用于抑制 list_orchestration_tasks 空转。
 */
public final class SchedulerRunContext {

    /** 工具返回中含此标记时，JChatMind 应结束本轮循环 */
    public static final String IDLE_DIRECTIVE_PREFIX = "【调度指令】";

    private static final ThreadLocal<Integer> LIST_POLL_COUNT = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CREATE_COUNT = ThreadLocal.withInitial(() -> 0);

    private SchedulerRunContext() {
    }

    public static void begin() {
        LIST_POLL_COUNT.set(0);
        CREATE_COUNT.set(0);
    }

    public static void clear() {
        LIST_POLL_COUNT.remove();
        CREATE_COUNT.remove();
    }

    public static void incrementCreate() {
        CREATE_COUNT.set(CREATE_COUNT.get() + 1);
    }

    public static int getCreateCount() {
        return CREATE_COUNT.get();
    }

    public static int incrementListPoll() {
        int next = LIST_POLL_COUNT.get() + 1;
        LIST_POLL_COUNT.set(next);
        return next;
    }

    public static int getListPollCount() {
        return LIST_POLL_COUNT.get();
    }
}
