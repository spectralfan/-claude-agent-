package com.kama.jchatmind.coding.context;

/**
 * 当前 Agent 运行线程绑定的聊天会话上下文，供 Coding 工具解析 task/session。
 */
public final class CodingSessionContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    private CodingSessionContext() {
    }

    public record Context(String sessionId, String agentId) {
    }

    public static void set(String sessionId, String agentId) {
        HOLDER.set(new Context(sessionId, agentId));
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
