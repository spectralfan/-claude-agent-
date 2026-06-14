package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodingFileHelper {
    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final ChatEventPublisher chatEventPublisher;

    public CodingTask requireActiveTask() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) throw new IllegalStateException("错误：无 Coding 会话上下文");
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) throw new IllegalStateException("错误：当前会话无活动 Coding 任务");
        return task;
    }

    public Path requireTaskWorkspace() { return codingWorkspaceService.resolveForTask(requireActiveTask()); }

    public Path resolveFilePath(String relativePath) {
        Path base = requireTaskWorkspace();
        Path target = (relativePath == null || relativePath.isBlank() || ".".equals(relativePath)) ? base : base.resolve(relativePath).normalize();
        if (!codingWorkspaceService.isPathSafe(base, target)) throw new IllegalStateException("错误：路径越界 - " + relativePath);
        return target;
    }

    public static List<String> parsePathList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,\\n]+")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    public void notifyFileChanged(String relativePath, String changeType, String oldContent, String newContent) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) return;
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) return;
        try {
            chatEventPublisher.publish(ctx.sessionId(), SseMessage.builder().type(SseMessage.Type.CODING_FILE_CHANGED)
                    .payload(SseMessage.Payload.builder().taskId(task.getId()).relativePath(relativePath).changeType(changeType).build()).build());
        } catch (Exception e) { log.warn("SSE failed: {}", e.getMessage()); }
    }
}