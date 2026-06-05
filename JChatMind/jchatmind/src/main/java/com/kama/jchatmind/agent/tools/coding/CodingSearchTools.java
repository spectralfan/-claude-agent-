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
import com.kama.jchatmind.realtime.RealtimeNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodingSearchTools implements Tool {

    private static final int MAX_MATCHES = 40;
    private static final int MAX_LINE_LEN = 240;

    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingChangeRegistry changeRegistry;
    private final CodingVerificationService codingVerificationService;
    private final RealtimeNotifier realtimeNotifier;
    private final CodingProperties codingProperties;

    @Override
    public String getName() {
        return "coding_search_tools";
    }

    @Override
    public String getDescription() {
        return "在 Coding 工作区内搜索文本或对文件做局部 patch";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "search_coding_files",
            description = "在工作区内按文本搜索（类 grep）。query 为要查找的子串；glob 可选如 *.java"
    )
    public String searchCodingFiles(String query, String glob) {
        if (!StringUtils.hasText(query)) {
            return "错误：query 不能为空";
        }
        try {
            Path base = requireTaskWorkspace();
            String globPattern = glob != null && !glob.isBlank() ? glob.trim() : "*";
            List<String> matches = new ArrayList<>();
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (shouldIgnoreDir(base, dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= MAX_MATCHES) {
                        return FileVisitResult.TERMINATE;
                    }
                    String rel = base.relativize(file).toString().replace("\\", "/");
                    if (!matchesGlob(rel, globPattern)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!isTextCandidate(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).toLowerCase(Locale.ROOT)
                                    .contains(query.toLowerCase(Locale.ROOT))) {
                                matches.add(rel + ":" + (i + 1) + ": "
                                        + truncate(lines.get(i)));
                                if (matches.size() >= MAX_MATCHES) {
                                    return FileVisitResult.TERMINATE;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // skip unreadable
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (matches.isEmpty()) {
                return "未找到匹配: " + query;
            }
            return "找到 " + matches.size() + " 处匹配:\n" + String.join("\n", matches);
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "错误：搜索失败 - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "apply_coding_patch",
            description = "对文件做局部替换：将 relativePath 中第一处 oldText 替换为 newText（须与文件内容完全匹配）"
    )
    public String applyCodingPatch(String relativePath, String oldText, String newText) {
        if (!StringUtils.hasText(relativePath)) {
            return "错误：relativePath 不能为空";
        }
        if (oldText == null) {
            return "错误：oldText 不能为空";
        }
        if (newText == null) {
            newText = "";
        }
        try {
            Path path = resolveFilePath(relativePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在 - " + relativePath;
            }
            String content = Files.readString(path);
            int idx = content.indexOf(oldText);
            if (idx < 0) {
                return "错误：未找到 oldText，请用 search_coding_files 或 read_coding_file 确认内容";
            }
            if (content.indexOf(oldText, idx + oldText.length()) >= 0) {
                return "错误：oldText 出现多次，请提供更精确的上下文片段";
            }
            String patched = content.substring(0, idx) + newText + content.substring(idx + oldText.length());
            Files.writeString(path, patched);
            String normalized = normalizeRelativePath(relativePath);
            notifyFileChanged(normalized, "modified", content, patched);
            CodingTask task = requireTask();
            changeRegistry.recordChange(task.getId(), normalized, "modified");
            codingVerificationService.invalidate(task.getId());
            return "成功 patch: " + normalized;
        } catch (IllegalStateException e) {
            return e.getMessage();
        } catch (IOException e) {
            return "错误：patch 失败 - " + e.getMessage();
        }
    }

    private CodingTask requireTask() {
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

    private Path requireTaskWorkspace() {
        return codingWorkspaceService.resolveForTask(requireTask());
    }

    private Path resolveFilePath(String relativePath) {
        Path base = requireTaskWorkspace();
        Path target = base.resolve(relativePath).normalize();
        if (!codingWorkspaceService.isPathSafe(base, target)) {
            throw new IllegalStateException("错误：路径越界 - " + relativePath);
        }
        return target;
    }

    private boolean shouldIgnoreDir(Path base, Path dir) {
        if (dir.equals(base)) {
            return false;
        }
        String name = dir.getFileName().toString();
        return codingProperties.getWorkspace().getIgnoreDirs().contains(name);
    }

    private boolean matchesGlob(String path, String glob) {
        if ("*".equals(glob) || glob == null || glob.isBlank()) {
            return true;
        }
        String pattern = glob.startsWith("*") ? glob : glob;
        if (pattern.startsWith("*.")) {
            return path.endsWith(pattern.substring(1));
        }
        return path.contains(pattern.replace("*", ""));
    }

    private boolean isTextCandidate(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Set<String> skip = Set.of(".jar", ".class", ".png", ".jpg", ".gif", ".zip", ".pdf");
        for (String ext : skip) {
            if (name.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String line) {
        if (line.length() <= MAX_LINE_LEN) {
            return line.trim();
        }
        return line.substring(0, MAX_LINE_LEN).trim() + "…";
    }

    private static String normalizeRelativePath(String relativePath) {
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
        int maxChars = codingProperties.getWorkspace().getSseDiffMaxChars();
        realtimeNotifier.tryPublish(ctx.sessionId(), SseMessage.builder()
                .type(SseMessage.Type.CODING_FILE_CHANGED)
                .payload(SseMessage.Payload.builder()
                        .taskId(task.getId())
                        .relativePath(relativePath)
                        .changeType(changeType)
                        .oldContent(truncateContent(oldContent, maxChars))
                        .newContent(truncateContent(newContent, maxChars))
                        .statusText(("modified".equals(changeType) ? "修改" : "变更") + ": " + relativePath)
                        .build())
                .build());
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
