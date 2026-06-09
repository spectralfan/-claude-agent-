package com.kama.jchatmind.coding.controller;

import com.kama.jchatmind.coding.model.dto.CodingRuntimeStatusDTO;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import com.kama.jchatmind.mcp.bootstrap.McpShellHealthService;
import com.kama.jchatmind.mcp.bridge.McpShellCommandPolicy;
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
    private final McpShellHealthService shellHealthService;

    @Value("${spring.ai.mcp.client.enabled:false}")
    private boolean springMcpClientEnabled;

    @GetMapping("/tasks/session/{sessionId}/subtasks")
    public ApiResponse<List<CodingSubtaskDTO>> listSubtasks(@PathVariable String sessionId) {
        return ApiResponse.success(codingSubtaskService.listByParentSession(sessionId));
    }

    /** DAG 编排任务列表（含 role、dependsOn） */
    @GetMapping("/orchestration/tasks")
    public ApiResponse<List<CodingSubtaskDTO>> listOrchestrationTasks(
            @org.springframework.web.bind.annotation.RequestParam String sessionId) {
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

    /** 联调：MCP shell 就绪状态（proxy 离线时 count=0） */
    @GetMapping("/mcp-health")
    public ApiResponse<Map<String, Object>> mcpHealth() {
        shellHealthService.ensureSelfTest(mcpProperties);
        List<ToolCallback> callbacks = mcpToolBridge.getAllToolCallbacks();
        boolean shellReady = callbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .anyMatch(name -> name.contains("execute_command")
                        || name.contains("shell_exec")
                        || name.contains("shell_execute"));
        Map<String, Object> body = new LinkedHashMap<>(shellHealthService.healthSnapshot(mcpProperties));
        body.put("mcpEnabled", mcpProperties.isEnabled());
        body.put("toolCount", callbacks.size());
        body.put("shellReady", shellReady);
        body.put("policyVersion", McpShellCommandPolicy.POLICY_VERSION);
        boolean selfTestOk = Boolean.TRUE.equals(body.get("selfTestPassed"));
        String hint;
        if (!shellReady) {
            hint = "MCP 终端未就绪：请启动 mcp-proxy 并重启后端；Windows 请确认 application.yaml mcp.shell.platform=auto";
        } else if (!selfTestOk) {
            hint = "MCP 已连接但 runner 自检未通过，请重启 mcp-proxy（start-mcp-proxy.ps1）并确认输出含 [runner: 2.3.1]";
        } else {
            hint = "MCP shell 已就绪（" + body.get("shellPlatform") + "/" + body.get("shellExecutor")
                    + "，runner=" + body.get("runnerVersion") + "）";
        }
        body.put("hint", hint);
        return ApiResponse.success(body);
    }
}
