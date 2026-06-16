package com.kama.jchatmind.mcp.permission;

import java.util.List;

/**
 * 每个工具的权限策略（对齐 KamaClaude ToolPolicy）。
 */
public class ToolPolicy {

    public enum Default {
        ALLOW, DENY, ASK
    }

    private final Default defaultDecision;
    private final List<String> allowPatterns;
    private final List<String> denyPatterns;

    public ToolPolicy(Default defaultDecision, List<String> allowPatterns, List<String> denyPatterns) {
        this.defaultDecision = defaultDecision;
        this.allowPatterns = allowPatterns != null ? allowPatterns : List.of();
        this.denyPatterns = denyPatterns != null ? denyPatterns : List.of();
    }

    public Default getDefaultDecision() { return defaultDecision; }
    public List<String> getAllowPatterns() { return allowPatterns; }
    public List<String> getDenyPatterns() { return denyPatterns; }

    /** 默认策略 */
    public static ToolPolicy allowDefault() {
        return new ToolPolicy(Default.ALLOW, List.of(), List.of());
    }

    public static ToolPolicy denyDefault() {
        return new ToolPolicy(Default.DENY, List.of(), List.of());
    }

    public static ToolPolicy askDefault() {
        return new ToolPolicy(Default.ASK, List.of(), List.of());
    }
}