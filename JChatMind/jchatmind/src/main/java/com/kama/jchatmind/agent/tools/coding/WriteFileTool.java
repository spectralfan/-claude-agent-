package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.registry.CodingChangeRegistry;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
@RequiredArgsConstructor
public class WriteFileTool implements Tool {
    private final CodingFileHelper helper;
    private final CodingTaskService codingTaskService;
    private final CodingChangeRegistry changeRegistry;
    private final CodingVerificationService codingVerificationService;

    @Override public String getName() { return "write_coding_file"; }
    @Override public String getDescription() { return "写入 Coding 工作区文件"; }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(name = "write_coding_file", description = "写入文件，不存在则创建，存在则覆盖")
    public String writeFile(String relativePath, String content) {
        try {
            Path path = helper.resolveFilePath(relativePath);
            String oldContent = Files.exists(path) ? Files.readString(path) : null;
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content);
            log.info("写入: {}", path);
            String ct = oldContent == null ? "created" : "modified";
            helper.notifyFileChanged(relativePath.replace("\\", "/"), ct, oldContent, content);
            CodingTask task = codingTaskService.getActiveTask(CodingSessionContext.get().sessionId());
            if (task != null) { changeRegistry.recordChange(task.getId(), relativePath, ct); codingVerificationService.invalidate(task.getId()); }
            return "成功写入: " + relativePath;
        } catch (Exception e) { return e instanceof IllegalStateException ? e.getMessage() : "错误：" + e.getMessage(); }
    }

    @org.springframework.ai.tool.annotation.Tool(name = "append_coding_file", description = "向已存在文件追加内容")
    public String appendFile(String relativePath, String content) {
        try {
            Path path = helper.resolveFilePath(relativePath);
            if (!Files.exists(path)) return "错误：文件不存在";
            String oldContent = Files.readString(path);
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            helper.notifyFileChanged(relativePath.replace("\\", "/"), "modified", oldContent, oldContent + content);
            CodingTask task = codingTaskService.getActiveTask(CodingSessionContext.get().sessionId());
            if (task != null) { changeRegistry.recordChange(task.getId(), relativePath, "modified"); codingVerificationService.invalidate(task.getId()); }
            return "成功追加: " + relativePath + " (+" + content.length() + " 字符)";
        } catch (Exception e) { return e instanceof IllegalStateException ? e.getMessage() : "错误：" + e.getMessage(); }
    }
}