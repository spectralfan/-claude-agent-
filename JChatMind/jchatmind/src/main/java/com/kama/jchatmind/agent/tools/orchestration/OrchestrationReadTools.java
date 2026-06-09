package com.kama.jchatmind.agent.tools.orchestration;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.OrchestrationProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestrationReadTools implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final OrchestrationProperties orchestrationProperties;

    @Override
    public String getName() {
        return "orchestration_read_tools";
    }

    @Override
    public String getDescription() {
        return "只读查看工作区文件与目录（Scheduler/Reviewer）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "read_workspace_file",
            description = "只读读取工作区文件，最大 512KB，超出截断并标注 truncated=true。"
    )
    public String readWorkspaceFile(String relativePath) {
        try {
            Path path = resolveFilePath(relativePath);
            if (!Files.exists(path)) {
                return "错误：文件不存在 - " + relativePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误：路径不是文件 - " + relativePath;
            }
            byte[] bytes = Files.readAllBytes(path);
            int maxBytes = orchestrationProperties.getReadMaxBytes();
            boolean truncated = bytes.length > maxBytes;
            String content = new String(
                    truncated ? java.util.Arrays.copyOf(bytes, maxBytes) : bytes,
                    StandardCharsets.UTF_8);
            return "truncated=" + truncated + " size=" + bytes.length + "\n" + content;
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            log.error("读取文件失败: {}", relativePath, e);
            return "错误：读取失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "list_workspace_dir",
            description = "列出工作区目录树。relativePath 默认根；maxDepth 默认 2，上限 5。"
    )
    public String listWorkspaceDir(String relativePath, Integer maxDepth) {
        try {
            CodingTask task = requireActiveTask();
            Path base = codingWorkspaceService.resolveForTask(task);
            String rel = relativePath == null || relativePath.isBlank() ? "." : relativePath;
            Path dir = base.resolve(rel).normalize();
            if (!codingWorkspaceService.isPathSafe(base, dir)) {
                return "错误：路径越界 - " + relativePath;
            }
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return "错误：目录不存在 - " + relativePath;
            }
            int depth = maxDepth != null ? maxDepth : orchestrationProperties.getListDirDefaultDepth();
            depth = Math.min(depth, orchestrationProperties.getListDirMaxDepth());
            List<String> lines = new ArrayList<>();
            walkDir(dir, base, 0, depth, lines);
            if (lines.isEmpty()) {
                return "(空目录)";
            }
            return String.join("\n", lines);
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "错误：列出目录失败 - " + e.getMessage();
        }
    }

    private void walkDir(Path dir, Path base, int currentDepth, int maxDepth, List<String> lines)
            throws IOException {
        if (currentDepth > maxDepth) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> children = stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path child : children) {
                String rel = base.relativize(child).toString().replace('\\', '/');
                boolean isDir = Files.isDirectory(child);
                lines.add((isDir ? "[D] " : "[F] ") + rel);
                if (isDir && currentDepth < maxDepth) {
                    walkDir(child, base, currentDepth + 1, maxDepth, lines);
                }
            }
        }
    }

    private Path resolveFilePath(String relativePath) {
        CodingTask task = requireActiveTask();
        Path base = codingWorkspaceService.resolveForTask(task);
        Path target = base.resolve(relativePath).normalize();
        if (!codingWorkspaceService.isPathSafe(base, target)) {
            throw new IllegalStateException("错误：路径越界 - " + relativePath);
        }
        return target;
    }

    private CodingTask requireActiveTask() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            throw new IllegalStateException("错误：无 Coding 会话上下文");
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            throw new IllegalStateException("错误：当前会话无活动 Coding 任务");
        }
        return task;
    }
}
