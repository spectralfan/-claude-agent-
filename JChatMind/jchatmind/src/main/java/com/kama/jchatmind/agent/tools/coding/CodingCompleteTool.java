package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.context.SubAgentRunContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.registry.CodingChangeRegistry;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodingCompleteTool implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingChangeRegistry changeRegistry;
    private final RealtimeNotifier realtimeNotifier;
    private final CodingVerificationService codingVerificationService;

    @Override
    public String getName() {
        return "mark_coding_complete";
    }

    @Override
    public String getDescription() {
        return "验证通过后标记 Coding 任务完成并推送交付摘要";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "mark_coding_complete",
            description = "在验证命令全部通过后调用，写入任务完成摘要并通知前端。summary 应包含：改了哪些文件、如何运行。"
    )
    public String markComplete(String summary) {
        if (SubAgentRunContext.get() != null) {
            return "错误：Worker 子 Agent 不能标记父任务完成。请完成本子目标后结束；由 Orchestrator 汇总全部子任务后调用 mark_coding_complete。";
        }
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return "错误：无 Coding 会话上下文";
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return "错误：当前会话无活动 Coding 任务";
        }
        if (summary == null || summary.isBlank()) {
            return "错误：summary 不能为空";
        }
        var validationError = codingVerificationService.validateBeforeComplete(task.getId());
        if (validationError.isPresent()) {
            return validationError.get();
        }
        codingTaskService.completeTask(task.getId(), summary.trim());
        int changedCount = changeRegistry.getChangedFiles(task.getId()).size();
        realtimeNotifier.tryPublish(task.getSessionId(), SseMessage.builder()
                .type(SseMessage.Type.CODING_COMPLETED)
                .payload(SseMessage.Payload.builder()
                        .taskId(task.getId())
                        .summary(summary.trim())
                        .statusText("Coding 任务已完成")
                        .detail("变更文件数: " + changedCount)
                        .done(true)
                        .build())
                .build());
        return "任务已标记完成。变更文件数: " + changedCount;
    }
}
