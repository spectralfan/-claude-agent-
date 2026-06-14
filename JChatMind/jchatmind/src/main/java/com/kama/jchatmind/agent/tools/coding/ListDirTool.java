package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListDirTool implements Tool {
    private final CodingFileHelper helper;
    private final CodingWorkspaceService codingWorkspaceService;
    private final CodingProperties codingProperties;

    @Override public String getName() { return "list_coding_directory_tree"; }
    @Override public String getDescription() { return "列出 Coding 工作区目录"; }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(name = "list_coding_directory_tree", description = "递归列出目录树")
    public String listDirectoryTree(String relativePath, Integer maxDepth) {
        try {
            CodingTask task = helper.requireActiveTask();
            String rel = relativePath == null || relativePath.isBlank() ? "." : relativePath;
            int d = maxDepth != null ? maxDepth : codingProperties.getWorkspace().getListDirDefaultDepth();
            return codingWorkspaceService.listDirectoryTreeForTask(task, rel, Math.min(d, codingProperties.getWorkspace().getListDirMaxDepth()));
        } catch (Exception e) { return e.getMessage(); }
    }

    @org.springframework.ai.tool.annotation.Tool(name = "list_coding_directory", description = "列出单层目录")
    public String listDirectory(String relativePath) {
        try {
            Path dir = helper.resolveFilePath(relativePath == null || relativePath.isBlank() ? "." : relativePath);
            if (!Files.exists(dir)) return "错误：目录不存在";
            try (var stream = Files.list(dir)) {
                String listing = stream.sorted((a,b)->a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                        .map(p -> (Files.isDirectory(p) ? "[DIR] " : "[FILE] ") + p.getFileName()).collect(Collectors.joining("\n"));
                return listing.isEmpty() ? "(空目录)" : listing;
            }
        } catch (Exception e) { return e.getMessage(); }
    }
}