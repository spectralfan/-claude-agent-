package com.kama.jchatmind.session;

/**
 * Agent run 的结构化执行结果。
 */
public class RunOutcome {

    private final String status;    // success / failed / cancelled
    private final String result;    // 最终文字输出
    private final String reason;    // 失败原因
    private final int steps;        // 实际执行步数

    public RunOutcome(String status, String result, String reason, int steps) {
        this.status = status;
        this.result = result;
        this.reason = reason;
        this.steps = steps;
    }

    public static RunOutcome success(String result, int steps) {
        return new RunOutcome("success", result, null, steps);
    }

    public static RunOutcome failed(String reason, int steps) {
        return new RunOutcome("failed", null, reason, steps);
    }

    public boolean isSuccess() { return "success".equals(status); }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public int getSteps() { return steps; }
}