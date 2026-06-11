package com.kama.jchatmind.agent.tools.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SchedulerRunContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.dto.OrchestrationTaskDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskRole;
import com.kama.jchatmind.coding.model.enums.OrchestrationTaskStatus;
import com.kama.jchatmind.coding.model.dto.CodingAgentPresetDTO;
import com.kama.jchatmind.coding.service.CodingAgentPresetService;
import com.kama.jchatmind.coding.service.CodingReviewerPresetService;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.OrchestrationTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrchestrationTaskTools implements Tool {

    private final OrchestrationTaskService orchestrationTaskService;
    private final CodingTaskService codingTaskService;
    private final CodingAgentPresetService codingAgentPresetService;
    private final CodingReviewerPresetService codingReviewerPresetService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "orchestration_task_tools";
    }

    @Override
    public String getDescription() {
        return "编排任务 DAG：创建、更新、列表、详情（仅 Scheduler）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "create_orchestration_task",
            description = "创建编排子任务。role=WORKER|REVIEWER。"
                    + "大需求须拆成多个 WORKER，同一 assistant 轮可发起多个 create tool_calls 并行建图。"
                    + "goal 须具体：改哪些文件、验收命令、完成标准。"
                    + "dependsOn 为逗号分隔的任务 ID；无依赖的 WORKER 由系统自动并行执行。"
                    + "Worker COMPLETED 后系统通常自动创建 Reviewer，一般无需手建 REVIEWER。"
    )
    public String createOrchestrationTask(
            String role,
            String title,
            String goal,
            String constraints,
            String contextFiles,
            String dependsOn) {
        if (SubAgentRunContext.get() != null) {
            return "错误：子 Agent 不能创建编排任务";
        }
        CodingSessionContext.Context ctx = requireContext();
        CodingTask task = requireActiveTask(ctx.sessionId());
        OrchestrationTaskRole taskRole = parseRole(role);

        String agentId = taskRole == OrchestrationTaskRole.REVIEWER
                ? resolveReviewerAgentId()
                : resolveWorkerAgentId();
        if (agentId == null) {
            return "错误：未找到 " + taskRole.getCode() + " Agent 预设";
        }

        OrchestrationTaskDTO created = orchestrationTaskService.create(
                ctx.sessionId(),
                task.getId(),
                taskRole,
                title,
                goal,
                constraints,
                parseCsvList(contextFiles),
                parseCsvList(dependsOn),
                agentId,
                1,
                null,
                null
        );
        SchedulerRunContext.incrementCreate();
        int createdCount = SchedulerRunContext.getCreateCount();
        return "任务已创建 status=" + created.getStatus() + " taskId=" + created.getId()
                + " role=" + created.getRole()
                + "\n本轮已创建 " + createdCount + " 个任务。"
                + " 大需求须拆多个 WORKER：同一轮可并行多个 create_orchestration_task，或继续 create 直至拆完，再告知用户勿 list。";
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "update_orchestration_task",
            description = "更新 PENDING/READY 任务的状态、goal、constraints 或 dependsOn（逗号分隔 ID）。"
    )
    public String updateOrchestrationTask(
            String taskId,
            String status,
            String goal,
            String constraints,
            String dependsOn) {
        if (SubAgentRunContext.get() != null) {
            return "错误：子 Agent 不能更新编排任务";
        }
        requireContext();
        OrchestrationTaskDTO updated = orchestrationTaskService.update(
                taskId,
                status,
                goal,
                constraints,
                dependsOn != null ? parseCsvList(dependsOn) : null
        );
        return "任务已更新 taskId=" + updated.getId() + " status=" + updated.getStatus();
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "list_orchestration_tasks",
            description = "列出编排任务 DAG。仅在有 [系统自动继续] 或需决策失败/审查时调用；禁止轮询 RUNNING 任务。"
    )
    public String listOrchestrationTasks() {
        CodingSessionContext.Context ctx = requireContext();
        List<OrchestrationTaskDTO> list = orchestrationTaskService.listByParentSession(ctx.sessionId());
        if (list.isEmpty()) {
            return "当前会话暂无编排任务";
        }
        if (hasActiveTasks(list)) {
            int pollCount = SchedulerRunContext.incrementListPoll();
            if (pollCount > 1) {
                return SchedulerRunContext.IDLE_DIRECTIVE_PREFIX
                        + "：禁止重复 list 轮询。请直接回复用户并结束本轮，等待 [系统自动继续]。";
            }
        }
        Map<String, OrchestrationTaskDTO> byId = list.stream()
                .collect(Collectors.toMap(OrchestrationTaskDTO::getId, t -> t));
        StringBuilder sb = new StringBuilder("共 ").append(list.size()).append(" 个任务:\n");
        for (OrchestrationTaskDTO dto : list) {
            sb.append(formatSummaryLine(dto, byId)).append("\n");
        }
        if (hasActiveTasks(list)) {
            sb.append("\n\n").append(SchedulerRunContext.IDLE_DIRECTIVE_PREFIX)
                    .append("：存在未终态任务，Worker/Reviewer 后台执行中。"
                            + "请向用户说明已委派并**结束本轮**，勿再调用 list_orchestration_tasks。");
        }
        return sb.toString().trim();
    }

    private static boolean hasActiveTasks(List<OrchestrationTaskDTO> list) {
        return list.stream().anyMatch(dto -> {
            OrchestrationTaskStatus status = OrchestrationTaskStatus.fromCode(dto.getStatus());
            return status == OrchestrationTaskStatus.PENDING
                    || status == OrchestrationTaskStatus.READY
                    || status == OrchestrationTaskStatus.RUNNING;
        });
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "get_orchestration_task",
            description = "获取单个编排任务完整 JSON。"
    )
    public String getOrchestrationTask(String taskId) {
        requireContext();
        return orchestrationTaskService.findById(taskId)
                .map(this::toJson)
                .orElse("错误：未找到任务 " + taskId);
    }

    private String formatSummaryLine(OrchestrationTaskDTO dto, Map<String, OrchestrationTaskDTO> byId) {
        String deps = formatDeps(dto.getDependsOn(), byId);
        String goalLine = dto.getGoal() != null && dto.getGoal().length() > 80
                ? dto.getGoal().substring(0, 80) + "..."
                : dto.getGoal();
        String line = "- [%s] %s role=%s deps=[%s] title=%s | %s".formatted(
                dto.getStatus(), dto.getId(), dto.getRole(), deps, dto.getTitle(), goalLine);
        String outcome = formatTerminalOutcome(dto);
        return outcome != null ? line + " | " + outcome : line;
    }

    private String formatTerminalOutcome(OrchestrationTaskDTO dto) {
        OrchestrationTaskStatus status = OrchestrationTaskStatus.fromCode(dto.getStatus());
        if (status == OrchestrationTaskStatus.COMPLETED && dto.getResultSummary() != null
                && !dto.getResultSummary().isBlank()) {
            return "summary=" + truncate(dto.getResultSummary(), 200);
        }
        if (status == OrchestrationTaskStatus.FAILED && dto.getErrorMessage() != null
                && !dto.getErrorMessage().isBlank()) {
            return "error=" + truncate(dto.getErrorMessage(), 200);
        }
        return null;
    }

    private static String truncate(String text, int maxLen) {
        String oneLine = text.replace('\n', ' ').trim();
        if (oneLine.length() <= maxLen) {
            return oneLine;
        }
        return oneLine.substring(0, maxLen) + "...";
    }

    private String formatDeps(List<String> deps, Map<String, OrchestrationTaskDTO> byId) {
        if (deps == null || deps.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String dep : deps) {
            OrchestrationTaskDTO d = byId.get(dep);
            parts.add(dep + (d != null ? ":" + d.getStatus() : ":?"));
        }
        return String.join(",", parts);
    }

    private String toJson(OrchestrationTaskDTO dto) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("taskId", dto.getId());
            map.put("role", dto.getRole());
            map.put("title", dto.getTitle());
            map.put("goal", dto.getGoal());
            map.put("constraints", dto.getConstraints());
            map.put("contextFiles", dto.getContextFiles());
            map.put("dependsOn", dto.getDependsOn());
            map.put("status", dto.getStatus());
            map.put("depth", dto.getDepth());
            map.put("workerAgentId", dto.getWorkerAgentId());
            map.put("spawnedFromTaskId", dto.getSpawnedFromTaskId());
            map.put("resultSummary", dto.getResultSummary());
            map.put("errorMessage", dto.getErrorMessage());
            map.put("metadata", dto.getMetadata());
            map.put("createdAt", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
            map.put("startedAt", dto.getStartedAt() != null ? dto.getStartedAt().toString() : null);
            map.put("finishedAt", dto.getFinishedAt() != null ? dto.getFinishedAt().toString() : null);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (Exception e) {
            return "taskId=" + dto.getId() + " status=" + dto.getStatus();
        }
    }

    private List<String> parseCsvList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private OrchestrationTaskRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            return OrchestrationTaskRole.WORKER;
        }
        return OrchestrationTaskRole.fromCode(role);
    }

    private CodingSessionContext.Context requireContext() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            throw new IllegalStateException("错误：无 Coding 会话上下文");
        }
        return ctx;
    }

    private CodingTask requireActiveTask(String sessionId) {
        CodingTask task = codingTaskService.getActiveTask(sessionId);
        if (task == null) {
            throw new IllegalStateException("错误：当前会话无活动 Coding 任务");
        }
        return task;
    }

    private String resolveReviewerAgentId() {
        return codingReviewerPresetService.findPreset()
                .map(CodingAgentPresetDTO::getAgentId)
                .orElse(null);
    }

    private String resolveWorkerAgentId() {
        return codingAgentPresetService.findPreset()
                .map(CodingAgentPresetDTO::getAgentId)
                .orElse(null);
    }
}
