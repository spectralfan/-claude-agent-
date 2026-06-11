package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.registry.CodingChangeRegistry;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodingFileTools implements Tool {

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingChangeRegistry changeRegistry;
    private final ChatEventPublisher chatEventPublisher;
    private final CodingVerificationService codingVerificationService;
    private final CodingProperties codingProperties;

    @Override
    public String getName() {
        return "coding_file_tools";
    }

    @Override
    public String getDescription() {
        return "在当前 Coding 工作区内读取、写入、列出文件（路径相对于任务工作区根）";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "read_coding_files",
            description = """
                    批量读取多个工作区文件（一次调用替代多次 read_coding_file）。
                    relativePaths 为逗号或换行分隔的相对路径，最多 10 个；单文件超出长度会截断。"""
    )
    public String readFiles(String relativePaths) {
        List<String> paths = parsePathList(relativePaths);
        if (paths.isEmpty()) {
            return "错误：relativePaths 不能为空";
        }
        int maxFiles = codingProperties.getWorkspace().getBatchReadMaxFiles();
        if (paths.size() > maxFiles) {
            return "错误：单次最多读取 " + maxFiles + " 个文件，当前 " + paths.size() + " 个";
        }
        int maxChars = codingProperties.getWorkspace().getBatchReadMaxCharsPerFile();
        StringBuilder sb = new StringBuilder();
        for (String path : paths) {
            sb.append("=== ").append(path).append(" ===\n");
            try {
                Path file = resolveFilePath(path);
                if (!Files.exists(file) || !Files.isRegularFile(file)) {
                    sb.append("错误：文件不存在或不是文件\n\n");
                    continue;
                }
                String content = Files.readString(file);
                if (content.length() > maxChars) {
                    sb.append(content, 0, maxChars)
                            .append("\n...[truncated, total=").append(content.length()).append("]\n\n");
                } else {
                    sb.append(content).append("\n\n");
                }
            } catch (IllegalStateException e) {
                sb.append(e.getMessage()).append("\n\n");
            } catch (IOException e) {
                sb.append("错误：读取失败 - ").append(e.getMessage()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "read_coding_file",
            description = "读取 Coding 工作区内的单个文件。多文件请优先 read_coding_files 一次读完。"
    )
    public String readFile(String relativePath) {
        try {
            Path path = resolveFilePath(relativePath);
            if (!Files.exists(path)) {
                return "错误：文件不存在 - " + relativePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误：路径不是文件 - " + relativePath;
            }
            return Files.readString(path);
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            log.error("读取文件失败: {}", relativePath, e);
            return "错误：读取失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "write_coding_file",
            description = """
                    写入 Coding 工作区内的文件；不存在则创建，存在则覆盖。
                    大文件（HTML/JS>8KB）勿单次整文件写入，易触发 JSON 截断；请先写骨架再用 append_coding_file / apply_coding_patch 分块追加。"""
    )
    public String writeFile(String relativePath, String content) {
        try {
            Path path = resolveFilePath(relativePath);
            String normalizedPath = normalizeRelativePath(relativePath);
            String oldContent = Files.exists(path) && Files.isRegularFile(path)
                    ? Files.readString(path)
                    : null;
            String changeType = oldContent == null ? "created" : "modified";
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String newContent = content == null ? "" : content;
            Files.writeString(path, newContent);
            log.info("Coding 工作区写入文件: {}", path);
            notifyFileChanged(normalizedPath, changeType, oldContent, newContent);
            CodingSessionContext.Context ctx = CodingSessionContext.get();
            if (ctx != null) {
                CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
                if (task != null) {
                    changeRegistry.recordChange(task.getId(), normalizedPath, changeType);
                    codingVerificationService.invalidate(task.getId());
                }
            }
            return "成功写入: " + normalizedPath;
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            log.error("写入文件失败: {}", relativePath, e);
            return "错误：写入失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "append_coding_file",
            description = "向已存在文件末尾追加内容（大 HTML/JS 请多次 append，单次建议<8000字符）"
    )
    public String appendFile(String relativePath, String content) {
        try {
            Path path = resolveFilePath(relativePath);
            String normalizedPath = normalizeRelativePath(relativePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在，请先用 write_coding_file 创建 - " + relativePath;
            }
            String oldContent = Files.readString(path);
            String chunk = content == null ? "" : content;
            Files.writeString(path, chunk, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
            String newContent = oldContent + chunk;
            notifyFileChanged(normalizedPath, "modified", oldContent, newContent);
            CodingSessionContext.Context ctx = CodingSessionContext.get();
            if (ctx != null) {
                CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
                if (task != null) {
                    changeRegistry.recordChange(task.getId(), normalizedPath, "modified");
                    codingVerificationService.invalidate(task.getId());
                }
            }
            return "成功追加: " + normalizedPath + " (+" + chunk.length() + " 字符)";
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            log.error("追加文件失败: {}", relativePath, e);
            return "错误：追加失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "list_coding_directory_tree",
            description = """
                    递归列出 Coding 工作区目录树（一次调用替代逐层 list_coding_directory）。
                    relativePath 为空或 . 表示工作区根；maxDepth 默认 2，上限 5。"""
    )
    public String listDirectoryTree(String relativePath, Integer maxDepth) {
        try {
            CodingTask task = requireActiveTask();
            String rel = relativePath == null || relativePath.isBlank() ? "." : relativePath;
            int depth = maxDepth != null ? maxDepth : codingProperties.getWorkspace().getListDirDefaultDepth();
            depth = Math.min(depth, codingProperties.getWorkspace().getListDirMaxDepth());
            return codingWorkspaceService.listDirectoryTreeForTask(task, rel, depth);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "list_coding_directory",
            description = "仅列出单层目录。探索子目录请用 list_coding_directory_tree(maxDepth=2~5)。"
    )
    public String listDirectory(String relativePath) {
        try {
            Path dir = resolveFilePath(relativePath == null || relativePath.isBlank() ? "." : relativePath);
            if (!Files.exists(dir)) {
                return "错误：目录不存在 - " + relativePath;
            }
            if (!Files.isDirectory(dir)) {
                return "错误：路径不是目录 - " + relativePath;
            }
            try (var stream = Files.list(dir)) {
                String listing = stream
                        .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                        .map(p -> (Files.isDirectory(p) ? "[DIR] " : "[FILE] ") + p.getFileName())
                        .collect(Collectors.joining("\n"));
                return listing.isEmpty() ? "(空目录)" : listing;
            }
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            log.error("列出目录失败: {}", relativePath, e);
            return "错误：列出目录失败 - " + e.getMessage();
        }
    }

    private Path resolveFilePath(String relativePath) {
        Path base = requireTaskWorkspace();
        Path target = (relativePath == null || relativePath.isBlank() || ".".equals(relativePath))
                ? base
                : base.resolve(relativePath).normalize();
        if (!codingWorkspaceService.isPathSafe(base, target)) {
            throw new IllegalStateException("错误：路径越界，禁止访问 - " + relativePath);
        }
        return target;
    }

    private CodingTask requireActiveTask() {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            throw new IllegalStateException("错误：无 Coding 会话上下文，无法访问工作区文件");
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            throw new IllegalStateException("错误：当前会话无活动 Coding 任务，请先在 AI Coding 页创建任务");
        }
        return task;
    }

    private static List<String> parsePathList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private Path requireTaskWorkspace() {
        return codingWorkspaceService.resolveForTask(requireActiveTask());
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || ".".equals(relativePath)) {
            return ".";
        }
        return relativePath.replace("\\", "/");
    }

    private void notifyFileChanged(String relativePath, String changeType,
                                   String oldContent, String newContent) {
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return;
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return;
        }
        int maxChars = 16_000;
        try {
            chatEventPublisher.publish(ctx.sessionId(), SseMessage.builder()
                    .type(SseMessage.Type.CODING_FILE_CHANGED)
                    .payload(SseMessage.Payload.builder()
                            .taskId(task.getId())
                            .relativePath(relativePath)
                            .changeType(changeType)
                            .oldContent(truncateContent(oldContent, maxChars))
                            .newContent(truncateContent(newContent, maxChars))
                            .statusText(("created".equals(changeType) ? "新建" : "修改") + ": " + relativePath)
                            .build())
                    .build());
        } catch (Exception e) {
            log.warn("CODING_FILE_CHANGED SSE 推送失败: {}", e.getMessage());
        }
    }

    private static String truncateContent(String content, int maxChars) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n…[已截断]";
    }
}
