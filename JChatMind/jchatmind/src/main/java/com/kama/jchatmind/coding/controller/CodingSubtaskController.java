package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingRuntimeStatusDTO;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.mcp.bridge.McpToolBridge;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.model.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coding")
@RequiredArgsConstructor
public class CodingSubtaskController {

    private final CodingSubtaskService codingSubtaskService;
    private final McpProperties mcpProperties;
    private final CodingProperties codingProperties;
    private final McpToolBridge mcpToolBridge;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean springMcpClientEnabled;

    @GetMapping("/tasks/session/{sessionId}/subtasks")
    public ApiResponse<List<CodingSubtaskDTO>> listSubtasks(@PathVariable String sessionId) {
        return ApiResponse.success(codingSubtaskService.listByParentSession(sessionId));
    }

    @GetMapping("/runtime-status")
    public ApiResponse<CodingRuntimeStatusDTO> runtimeStatus() {
        return ApiResponse.success(CodingRuntimeStatusDTO.builder()
                .mcpEnabled(mcpProperties.isEnabled())
                .springMcpClientEnabled(springMcpClientEnabled)
                .deliveryRequireVerification(codingProperties.getDelivery().isRequireVerification())
                .build());
    }

    /** 联调：列出当前 MCP 桥接发现的工具名 */
    @GetMapping("/mcp-tools")
    public ApiResponse<Map<String, Object>> listMcpTools() {
        List<ToolCallback> callbacks = mcpToolBridge.getAllToolCallbacks();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", callbacks.size());
        body.put("names", callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList());
        return ApiResponse.success(body);
    }
}
