package com.kama.jchatmind.mcp.integration;

import com.kama.jchatmind.mcp.model.dto.McpCallResultDTO;
import org.springframework.ai.tool.ToolCallback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP 与 Think-Execute 主流程的集成点。
 */
public interface McpIntegration {

    /**
     * 按 Agent 的 allowedTools 白名单返回可注入的 MCP 工具回调。
     * 白名单为空则不注入任何 MCP 工具。
     */
    List<ToolCallback> getToolsForAgent(List<String> allowedToolNames);

    /** 调用历史查询。 */
    List<McpCallResultDTO> getCallHistory(String serverId, String toolName, LocalDateTime since, int limit);

    /** 工具用量统计：tool_name -> 调用次数。 */
    Map<String, Long> getToolUsageStats(String serverId);
}
