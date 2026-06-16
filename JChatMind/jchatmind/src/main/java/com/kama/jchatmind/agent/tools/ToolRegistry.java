package com.kama.jchatmind.agent.tools;

import com.kama.jchatmind.agent.profile.AgentProfile;
import com.kama.jchatmind.coding.bridge.PermissionAwareToolCallback;
import com.kama.jchatmind.mcp.integration.McpIntegration;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.service.ToolFacadeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final String KNOWLEDGE_TOOL_NAME = "KnowledgeTool";

    private final com.kama.jchatmind.mcp.permission.PermissionManager pm;


    private final ToolFacadeService toolFacadeService;
    private final McpIntegration mcpIntegration;
    private final com.kama.jchatmind.coding.service.CodingTaskService codingTaskService;

    public ToolRegistry(ToolFacadeService tfs, McpIntegration mcp,
                        com.kama.jchatmind.coding.service.CodingTaskService cts,
                        com.kama.jchatmind.mcp.permission.PermissionManager pm) {

        this.toolFacadeService = tfs;
        this.mcpIntegration = mcp;
        this.codingTaskService = cts;
        this.pm = pm;
    }

    /** Build tool callbacks for a session (main agent or DB sub-agent). */
    public List<ToolCallback> buildForSession(AgentDTO agent, String sessionId) {
        boolean hasTask = sessionId != null && codingTaskService.getActiveTask(sessionId) != null;
        List<Tool> selected = new ArrayList<>(toolFacadeService.getFixedTools());
        if (hasTask) selected.removeIf(t -> KNOWLEDGE_TOOL_NAME.equals(t.getName()));

        List<String> allowed = agent.getAllowedTools();
        if (allowed != null && !allowed.isEmpty()) {
            Map<String, Tool> optMap = toolFacadeService.getOptionalTools().stream()
                    .collect(Collectors.toMap(Tool::getName, Function.identity()));
            for (String n : allowed) {
                Tool t = optMap.get(n);
                if (t != null) selected.add(t);
            }
        }
        if (!hasTask) selected.removeIf(t -> isCodingWorkspaceTool(t.getName()));

        List<ToolCallback> cbs = toCallbacks(selected);
        injectMcp(cbs, allowed, hasTask);
        return cbs;
    }

    /** Build tool callbacks for a profile sub-agent (no session filtering). */
    public List<ToolCallback> buildForProfile(AgentProfile profile) {
        List<String> pt = profile.getAllowedTools();
        Set<String> as = pt != null ? Set.copyOf(pt) : Set.of();
        List<Tool> selected = new ArrayList<>();
        for (Tool t : toolFacadeService.getAllTools()) {
            if ((t.getType() == ToolType.FIXED && !KNOWLEDGE_TOOL_NAME.equals(t.getName()))
                    || as.contains(t.getName())) {
                selected.add(t);
            }
        }
        List<ToolCallback> cbs = toCallbacks(selected);
        injectMcp(cbs, pt, true);
        return cbs;
    }

    private List<ToolCallback> toCallbacks(List<Tool> tools) {
        List<ToolCallback> r = new ArrayList<>();
        for (Tool t : tools) {
            Object target = AopUtils.isAopProxy(t) ? AopUtils.getTargetClass(t) : t;
            for (ToolCallback cb : MethodToolCallbackProvider.builder()
                    .toolObjects(target).build().getToolCallbacks()) {
                if (pm != null && !"direct_answer".equals(cb.getToolDefinition().name())) {
                    cb = new PermissionAwareToolCallback(cb, pm);
                }
                r.add(cb);
            }
        }
        return r;
    }

    private void injectMcp(List<ToolCallback> target, List<String> allowed, boolean hasTask) {
        try {
            Set<String> names = new LinkedHashSet<>();
            for (ToolCallback cb : target) names.add(cb.getToolDefinition().name());
            List<ToolCallback> merged = new ArrayList<>();
            List<String> filter = allowed != null ? allowed : List.of();
            for (ToolCallback cb : mcpIntegration.getToolsForAgent(filter)) {
                if (names.add(cb.getToolDefinition().name())) merged.add(cb);
            }
            target.addAll(merged);
        } catch (Exception e) {
            log.warn("MCP inject failed: {}", e.getMessage());
        }
    }

    public static boolean isCodingWorkspaceTool(String name) {
        return name != null && (name.startsWith("read_coding") || name.startsWith("write_coding") || name.startsWith("list_coding") || name.contains("coding_search") || name.contains("coding_verify") || name.contains("orchestration_")
                || name.equals("mark_coding_complete") || name.equals("delegate_coding_task")
                || name.equals("bash") || name.equals("run_terminal_cmd") || name.equals("maven_command")
                || name.equals("shell") || name.equals("shell_exec") || name.equals("shell_execute"));
    }
}
