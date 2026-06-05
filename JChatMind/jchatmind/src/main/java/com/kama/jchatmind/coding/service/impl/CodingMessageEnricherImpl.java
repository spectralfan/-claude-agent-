package com.kama.jchatmind.coding.service.impl;

import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.model.dto.CodingFileContentDTO;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingMessageEnricher;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CodingMessageEnricherImpl implements CodingMessageEnricher {

    /** 匹配 @src/main/java/Foo.java 或 @"path with spaces" */
    private static final Pattern AT_FILE = Pattern.compile("@(\"([^\"]+)\"|([^\\s@]+))");

    private final CodingWorkspaceService workspaceService;
    private final CodingProperties codingProperties;

    @Override
    public String enrichUserMessage(String userMessage, CodingTask task) {
        if (!StringUtils.hasText(userMessage) || task == null) {
            return userMessage;
        }
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = AT_FILE.matcher(userMessage);
        while (matcher.find()) {
            String path = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (StringUtils.hasText(path) && !".".equals(path)) {
                paths.add(path.trim());
            }
        }
        if (paths.isEmpty()) {
            return userMessage;
        }

        StringBuilder sb = new StringBuilder(userMessage);
        int maxChars = codingProperties.getWorkspace().getAtFileMaxChars();
        for (String path : paths) {
            try {
                CodingFileContentDTO file = workspaceService.readFileForTask(task, path);
                sb.append("\n\n<file path=\"")
                        .append(file.getRelativePath())
                        .append("\">\n");
                String content = file.getContent();
                if (content != null && content.length() > maxChars) {
                    content = content.substring(0, maxChars) + "\n…[文件内容已截断]";
                }
                sb.append(content == null ? "" : content);
                sb.append("\n</file>");
            } catch (Exception e) {
                sb.append("\n\n<file path=\"")
                        .append(path)
                        .append("\">\n[无法读取: ")
                        .append(e.getMessage())
                        .append("]\n</file>");
            }
        }
        return sb.toString();
    }
}
