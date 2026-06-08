package com.kama.jchatmind.mcp.bridge;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * MCP 终端不可用时的占位回调，避免 LLM 调用 run_terminal_cmd 等名称时 ToolCallingManager 抛错。
 */
public class FallbackTerminalToolCallback implements ToolCallback {

    private static final String FALLBACK_MESSAGE =
            "错误：MCP 终端未就绪。请确认 mcp-proxy 在 :3000 运行且 spring.ai.mcp.client.enabled=true。"
                    + " Java 项目可改用 maven_command（goal=compile 或 test）；"
                    + " 纯静态/HTML 任务可只改文件后 mark_coding_complete。";

    private final ToolDefinition definition;

    public FallbackTerminalToolCallback(String toolName) {
        this.definition = DefaultToolDefinition.builder()
                .name(toolName)
                .description("终端命令（当前 MCP 未连接，调用将返回提示信息）")
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "command": { "type": "string", "description": "Shell command" }
                          }
                        }
                        """)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    public String call(String toolInput) {
        return FALLBACK_MESSAGE;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return FALLBACK_MESSAGE;
    }
}
