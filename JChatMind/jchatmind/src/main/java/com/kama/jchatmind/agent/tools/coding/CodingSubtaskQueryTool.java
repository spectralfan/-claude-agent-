package com.kama.jchatmind.agent.tools.coding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.dto.CodingSubtaskDTO;
import com.kama.jchatmind.coding.service.CodingSubtaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CodingSubtaskQueryTool implements Tool {

    private final CodingSubtaskService codingSubtaskService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "coding_subtask_tools";
    }

    @Override
    public String getDescription() {
        return "查询异步 Coding 子任务状态与列表";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "get_coding_subtask_status",
            description = "按 subTaskId 查询异步子任务状态。COMPLETED 时返回 resultSummary。"
    )
    public String getCodingSubtaskStatus(String subTaskId) {
        if (subTaskId == null || subTaskId.isBlank()) {
            return "错误：subTaskId 不能为空";
        }
        Optional<CodingSubtaskDTO> opt = codingSubtaskService.findById(subTaskId.trim());
        if (opt.isEmpty()) {
            return "错误：未找到子任务 " + subTaskId;
        }
        CodingSubtaskDTO dto = opt.get();
        if (!belongsToCurrentSession(dto)) {
            return "错误：无权访问该子任务";
        }
        return formatSubtask(dto);
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "list_coding_subtasks",
            description = "列出当前 Coding 会话下所有异步子任务（按创建时间倒序）"
    )
    public String listCodingSubtasks() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return "错误：无 Coding 会话上下文";
        }
        List<CodingSubtaskDTO> list = codingSubtaskService.listByParentSession(ctx.sessionId());
        if (list.isEmpty()) {
            return "当前会话暂无子任务";
        }
        StringBuilder sb = new StringBuilder("共 ").append(list.size()).append(" 个子任务:\n");
        for (CodingSubtaskDTO dto : list) {
            sb.append("- ").append(formatSubtask(dto)).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean belongsToCurrentSession(CodingSubtaskDTO dto) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        return ctx != null && ctx.sessionId().equals(dto.getParentSessionId());
    }

    private String formatSubtask(CodingSubtaskDTO dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("subTaskId", dto.getId());
        map.put("title", dto.getTitle());
        map.put("status", dto.getStatus());
        map.put("goal", dto.getGoal());
        if (dto.getResultSummary() != null) {
            map.put("resultSummary", dto.getResultSummary());
        }
        if (dto.getErrorMessage() != null) {
            map.put("errorMessage", dto.getErrorMessage());
        }
        if (dto.getFinishedAt() != null) {
            map.put("finishedAt", dto.getFinishedAt().toString());
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "subTaskId=" + dto.getId() + " status=" + dto.getStatus();
        }
    }
}
