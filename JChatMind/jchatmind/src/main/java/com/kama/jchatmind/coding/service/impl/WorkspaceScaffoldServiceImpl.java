package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingStackService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.WorkspaceScaffoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceScaffoldServiceImpl implements WorkspaceScaffoldService {

    private final CodingProperties codingProperties;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingStackService codingStackService;

    @Override
    public List<String> scaffoldIfNeeded(CodingTask task, String stackId, boolean scaffoldOnCreate) {
        if (!scaffoldOnCreate || stackId == null || stackId.isBlank()) {
            return List.of();
        }
        var stackOpt = codingStackService.findById(stackId);
        if (stackOpt.isEmpty()) {
            throw new IllegalArgumentException("未知技术栈: " + stackId);
        }
        String templateId = stackOpt.get().getScaffoldTemplate();
        if (templateId == null || templateId.isBlank()) {
            templateId = stackId;
        }
        Path workspace = codingWorkspaceService.resolveForTask(task);
        if (!isScaffoldable(workspace)) {
            if (!codingProperties.getScaffold().isAllowOnNonEmpty()) {
                throw new IllegalStateException("工作区非空，无法脚手架初始化");
            }
        }
        String pattern = "classpath:" + codingProperties.getScaffold().getTemplatesPath()
                + "/" + templateId + "/**";
        List<String> copied = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
            String prefix = "coding-templates/" + templateId + "/";
            for (Resource resource : resources) {
                if (!resource.isReadable() || !resource.exists()) {
                    continue;
                }
                String uri = resource.getURI().toString();
                int idx = uri.indexOf(prefix);
                if (idx < 0) {
                    continue;
                }
                String relative = uri.substring(idx + prefix.length());
                if (relative.isBlank() || relative.endsWith("/")) {
                    continue;
                }
                Path target = workspace.resolve(relative).normalize();
                if (!codingWorkspaceService.isPathSafe(workspace, target)) {
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, target);
                }
                copied.add(relative.replace('\\', '/'));
            }
        } catch (IOException e) {
            throw new IllegalStateException("脚手架复制失败: " + e.getMessage(), e);
        }
        if (copied.isEmpty()) {
            log.warn("栈 {} 未找到模板资源: {}", stackId, pattern);
        } else {
            log.info("任务 {} 脚手架复制 {} 个文件", task.getId(), copied.size());
        }
        return copied;
    }

    private boolean isScaffoldable(Path dir) {
        if (!Files.exists(dir)) {
            return true;
        }
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.noneMatch(p -> {
                String name = p.getFileName().toString();
                return !name.equals(".") && !name.equals("..");
            });
        } catch (IOException e) {
            return false;
        }
    }
}
