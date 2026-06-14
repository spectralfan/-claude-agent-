package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.config.CodingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchReadTool implements Tool {
    private final CodingFileHelper helper;
    private final CodingProperties codingProperties;

    @Override public String getName() { return "read_coding_files"; }
    @Override public String getDescription() { return "批量读取多个工作区文件"; }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(name = "read_coding_files", description = "批量读取多个工作区文件，relativePaths 逗号或换行分隔")
    public String readFiles(String relativePaths) {
        List<String> paths = CodingFileHelper.parsePathList(relativePaths);
        if (paths.isEmpty()) return "错误：relativePaths 不能为空";
        int maxFiles = codingProperties.getWorkspace().getBatchReadMaxFiles();
        if (paths.size() > maxFiles) return "错误：单次最多读取 " + maxFiles + " 个文件";
        int maxChars = codingProperties.getWorkspace().getBatchReadMaxCharsPerFile();
        StringBuilder sb = new StringBuilder();
        for (String p : paths) {
            sb.append("=== ").append(p).append(" ===\n");
            try {
                Path f = helper.resolveFilePath(p);
                if (!Files.exists(f) || !Files.isRegularFile(f)) { sb.append("错误：文件不存在\n\n"); continue; }
                String content = Files.readString(f);
                if (content.length() > maxChars) sb.append(content, 0, maxChars).append("\n...[截断]\n\n");
                else sb.append(content).append("\n\n");
            } catch (Exception e) { sb.append(e.getMessage()).append("\n\n"); }
        }
        return sb.toString().trim();
    }
}