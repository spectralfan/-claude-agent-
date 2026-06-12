package com.kama.jchatmind.agent.tools;

public abstract class BaseTool implements Tool {

    public abstract String getName();
    public abstract String getDescription();

    @Override
    public ToolType getType() { return ToolType.OPTIONAL; }

    /** 统一执行入口，子类可覆盖此方法替代分散的 @Tool 方法 */
    public ToolResult execute(java.util.Map<String, Object> params) {
        return ToolResult.error("Not implemented: " + getName());
    }
}