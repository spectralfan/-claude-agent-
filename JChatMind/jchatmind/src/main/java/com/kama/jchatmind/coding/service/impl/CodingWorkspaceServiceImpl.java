package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingFileContentDTO;
import com.kama.jchatmind.coding.model.dto.CodingWorkspaceOptionDTO;
import com.kama.jchatmind.coding.model.dto.FileNode;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CodingWorkspaceServiceImpl implements CodingWorkspaceService {

    private final CodingProperties codingProperties;

    /** 规范化后的白名单根路径 */
    private List<Path> allowedRootPaths = List.of();
    private Path defaultRootPath;

    @PostConstruct
    void initRoots() {
        defaultRootPath = normalizeAndCreate(codingProperties.getWorkspace().getRoot());
        List<Path> roots = new ArrayList<>();
        roots.add(defaultRootPath);
        for (CodingProperties.AllowedRoot entry : codingProperties.getWorkspace().getAllowedRoots()) {
            if (entry == null || entry.getPath() == null || entry.getPath().isBlank()) {
                continue;
            }
            Path p = normalizePathOnly(entry.getPath());
            if (roots.stream().noneMatch(r -> r.equals(p))) {
                roots.add(p);
            }
        }
        allowedRootPaths = List.copyOf(roots);
    }

    @Override
    public List<CodingWorkspaceOptionDTO> listWorkspaceOptions() {
        var allowed = codingProperties.getWorkspace().getAllowedRoots();
        List<CodingWorkspaceOptionDTO> options = new ArrayList<>();
        if (allowed == null || allowed.isEmpty()) {
            options.add(defaultWorkspaceOption());
            return options;
        }
        options.add(defaultWorkspaceOption());
        for (CodingProperties.AllowedRoot entry : allowed) {
            if (entry.getPath() == null || entry.getPath().isBlank()) {
                continue;
            }
            Path resolved = normalizePathOnly(entry.getPath());
            if (resolved.equals(defaultRootPath)) {
                continue;
            }
            String label = entry.getName() != null && !entry.getName().isBlank()
                    ? entry.getName()
                    : resolved.getFileName().toString();
            options.add(CodingWorkspaceOptionDTO.builder()
                    .label(label)
                    .path(resolved.toString())
                    .defaultOption(false)
                    .build());
        }
        options.sort(Comparator.comparing(CodingWorkspaceOptionDTO::getLabel));
        return options;
    }

    private CodingWorkspaceOptionDTO defaultWorkspaceOption() {
        return CodingWorkspaceOptionDTO.builder()
                .label("默认沙箱 (workspace)")
                .path(defaultRootPath.toString())
                .defaultOption(true)
                .build();
    }

    @Override
    public Path getWorkspaceRoot() {
        return defaultRootPath;
    }

    @Override
    public Path resolveAllowedRoot(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return defaultRootPath;
        }
        Path candidate = normalizePathOnly(workspaceRoot);
        for (Path allowed : allowedRootPaths) {
            if (sameRoot(allowed, candidate)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException(
                "工作区不在允许列表中，请从网页下拉选择或在 application.yaml 配置 coding.workspace.allowed-roots: "
                        + workspaceRoot);
    }

    @Override
    public Path resolveSafePath(String relativePath) {
        return resolveSafePath(defaultRootPath, relativePath);
    }

    @Override
    public Path resolveForTask(CodingTask task) {
        Path base = resolveAllowedRoot(task.getWorkspaceRoot());
        String sub = task.getWorkspacePath();
        return resolveSafePath(base, (sub == null || sub.isBlank()) ? "." : sub);
    }

    private Path resolveSafePath(Path base, String relativePath) {
        Path target = (relativePath == null || relativePath.isBlank())
                ? base
                : base.resolve(relativePath).normalize();
        if (!isPathSafe(base, target)) {
            throw new IllegalArgumentException("路径越界，禁止访问: " + relativePath);
        }
        return target;
    }

    @Override
    public boolean isPathSafe(Path base, Path target) {
        try {
            Path baseReal = Files.exists(base) ? base.toRealPath() : base.toAbsolutePath().normalize();
            Path targetReal = Files.exists(target)
                    ? target.toRealPath()
                    : target.toAbsolutePath().normalize();
            return targetReal.startsWith(baseReal);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public List<FileNode> getProjectTree(String relativePath) {
        return listDirectoryAt(getWorkspaceRoot(), relativePath, getWorkspaceRoot());
    }

    @Override
    public List<FileNode> listDirectoryForTask(CodingTask task, String relativePath) {
        Path base = resolveForTask(task);
        return listDirectoryAt(base, relativePath, base);
    }

    @Override
    public CodingFileContentDTO readFileForTask(CodingTask task, String relativePath) {
        Path base = resolveForTask(task);
        Path target = resolveRelativePath(base, relativePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("文件不存在或不是普通文件: " + relativePath);
        }
        try {
            long size = Files.size(target);
            String content = Files.readString(target);
            int maxChars = codingProperties.getWorkspace().getPreviewMaxChars();
            boolean truncated = content.length() > maxChars;
            if (truncated) {
                content = content.substring(0, maxChars);
            }
            return CodingFileContentDTO.builder()
                    .relativePath(toRelativePath(base, target))
                    .content(content)
                    .size(size)
                    .truncated(truncated)
                    .language(inferLanguage(target.getFileName().toString()))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + relativePath, e);
        }
    }

    private List<FileNode> listDirectoryAt(Path base, String relativePath, Path relativizeBase) {
        Path target = resolveRelativePath(base, relativePath);
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            return List.of();
        }
        Set<String> ignoreDirs = codingProperties.getWorkspace().getIgnoreDirs().stream()
                .collect(Collectors.toSet());
        try (var stream = Files.list(target)) {
            List<FileNode> nodes = new ArrayList<>();
            stream.filter(p -> !shouldIgnore(p, ignoreDirs))
                    .sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> nodes.add(FileNode.builder()
                            .name(p.getFileName().toString())
                            .relativePath(toRelativePath(relativizeBase, p))
                            .directory(Files.isDirectory(p))
                            .build()));
            return nodes;
        } catch (IOException e) {
            throw new IllegalStateException("读取项目目录失败: " + target, e);
        }
    }

    private static boolean shouldIgnore(Path path, Set<String> ignoreDirs) {
        String name = path.getFileName().toString();
        return Files.isDirectory(path) && ignoreDirs.contains(name);
    }

    private Path resolveRelativePath(Path base, String relativePath) {
        Path target = (relativePath == null || relativePath.isBlank() || ".".equals(relativePath))
                ? base
                : base.resolve(relativePath).normalize();
        if (!isPathSafe(base, target)) {
            throw new IllegalArgumentException("路径越界，禁止访问: " + relativePath);
        }
        return target;
    }

    private static String toRelativePath(Path base, Path target) {
        return base.relativize(target.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }

    private static String inferLanguage(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "text";
        }
        return switch (fileName.substring(dot + 1).toLowerCase()) {
            case "java" -> "java";
            case "xml" -> "xml";
            case "yaml", "yml" -> "yaml";
            case "json" -> "json";
            case "md" -> "markdown";
            case "ts", "tsx" -> "typescript";
            case "js", "jsx" -> "javascript";
            case "css" -> "css";
            case "html", "htm" -> "html";
            case "properties" -> "properties";
            default -> "text";
        };
    }

    private Path normalizeAndCreate(Path path) {
        try {
            Files.createDirectories(path);
            return path.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("创建/解析工作区根目录失败: " + path, e);
        }
    }

    private Path normalizeAndCreate(String pathStr) {
        return normalizeAndCreate(Paths.get(pathStr).toAbsolutePath().normalize());
    }

    /** 解析路径并尽量 toRealPath，不强制创建目录（用于校验已有 IDEA 工程） */
    private Path normalizePathOnly(String pathStr) {
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();
        try {
            if (Files.exists(path)) {
                return path.toRealPath();
            }
            Files.createDirectories(path);
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    private boolean sameRoot(Path a, Path b) {
        if (a.equals(b)) {
            return true;
        }
        try {
            if (Files.exists(a) && Files.exists(b)) {
                return Files.isSameFile(a, b);
            }
        } catch (IOException ignored) {
            // fall through
        }
        return a.toAbsolutePath().normalize().toString()
                .equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
    }
}
