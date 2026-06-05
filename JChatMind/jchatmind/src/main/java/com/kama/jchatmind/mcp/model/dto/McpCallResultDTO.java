package com.kama.jchatmind.mcp.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具调用结果/历史出参。
 */
@Data
@Builder
public class McpCallResultDTO {

    private String serverId;

    private String toolName;

    private String status;

    private String result;

    private String errorMessage;

    private Integer durationMs;

    private LocalDateTime createdAt;
}
