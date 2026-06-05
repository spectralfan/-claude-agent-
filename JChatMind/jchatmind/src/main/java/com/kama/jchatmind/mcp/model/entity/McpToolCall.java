package com.kama.jchatmind.mcp.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具调用记录，对应表 t_mcp_tool_call。
 * JSON 列（arguments / result）以 String 形式承载，由上层序列化。
 */
@Data
@Builder
public class McpToolCall {

    private String id;

    /** MCP server / 连接名 */
    private String serverId;

    private String toolName;

    /** JSON string */
    private String arguments;

    /** JSON string */
    private String result;

    private String errorMessage;

    /** success / failed */
    private String status;

    private Integer durationMs;

    private String sessionId;

    private String agentId;

    private LocalDateTime createdAt;
}
