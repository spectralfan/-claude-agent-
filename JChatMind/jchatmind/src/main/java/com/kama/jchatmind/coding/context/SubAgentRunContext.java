package com.kama.jchatmind.coding.context;

/**
 * 子 Agent 运行上下文：SSE 推送到父会话，Coding 工具仍绑定父会话任务。
 */
public final class SubAgentRunContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private SubAgentRunContext() {
    }

    public record Context(String parentSessionId, String subTaskId, String title) {
    }

    public static void set(String parentSessionId, String subTaskId, String title) {
        HOLDER.set(new Context(parentSessionId, subTaskId, title));
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
