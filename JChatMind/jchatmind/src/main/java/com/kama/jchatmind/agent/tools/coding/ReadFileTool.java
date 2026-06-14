package com.kama.jchatmind.agent.tools.coding;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadFileTool implements Tool {
    private final CodingFileHelper helper;

    @Override public String getName() { return "read_coding_file"; }
    @Override public String getDescription() { return "读取单个工作区文件"; }
    @Override public ToolType getType() { return ToolType.OPTIONAL; }

    @org.springframework.ai.tool.annotation.Tool(name = "read_coding_file", description = "读取工作区内的单个文件")
    public String readFile(String relativePath) {
        try {
            Path f = helper.resolveFilePath(relativePath);
            if (!Files.exists(f)) return "错误：文件不存在";
            return Files.readString(f);
        } catch (Exception e) { return e.getMessage(); }
    }
}