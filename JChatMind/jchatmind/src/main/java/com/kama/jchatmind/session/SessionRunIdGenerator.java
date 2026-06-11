package com.kama.jchatmind.session;

import java.util.UUID;

/**
 * Session Run ID 生成器。
 *
 * <p>每个 run 有唯一的 run_id，格式为 run_<UUID 前 8 位>。</p>
 */
public final class SessionRunIdGenerator {

    private SessionRunIdGenerator() {}

    /**
     * 生成新 run ID。
     */
    public static String newRunId() {
        return "run_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 根据指定的 run ID 校验格式。
     */
    public static boolean isValidRunId(String runId) {
        return runId != null && runId.matches("^run_[a-f0-9]{8}$");
    }
}