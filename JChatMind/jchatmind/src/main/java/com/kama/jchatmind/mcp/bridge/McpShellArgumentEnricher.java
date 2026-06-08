package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.coding.context.CodingSessionContext;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.config.McpShellPlatform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP shell 工具参数防御层（Java 侧轻量预处理）。
 * 跨平台翻译与执行由 scripts/mcp/command-runner.mjs 负责（PowerShell@Win / sh@POSIX）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpShellArgumentEnricher {

    private final ObjectMapper objectMapper;
    private final CodingTaskService codingTaskService;
    private final CodingWorkspaceService codingWorkspaceService;
    private final McpProperties mcpProperties;

    public boolean isShellTool(String toolName) {
        return McpToolAliasRegistry.isTerminalToolName(toolName)
                || McpToolAliasRegistry.resolveCanonicalName(toolName) != null;
    }

    /**
     * @return 规范化后的 JSON 参数字符串；无法解析时原样返回
     */
    public String enrich(String toolName, String toolInput) {
        if (!isShellTool(toolName) || !StringUtils.hasText(toolInput)) {
            return toolInput;
        }
        try {
            JsonNode root = objectMapper.readTree(toolInput.trim());
            if (!root.isObject()) {
                return wrapBareCommand(toolInput);
            }
            ObjectNode obj = (ObjectNode) root;
            normalizeCommandField(obj);
            fillWorkingDirIfAbsent(obj);
            if (McpShellPlatform.useWindowsShell(mcpProperties.getShell())) {
                normalizeWindowsShellCommand(obj);
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.debug("MCP shell 参数非 JSON，按裸命令包装: {}", e.getMessage());
            return wrapBareCommand(toolInput);
        }
    }

    private void normalizeCommandField(ObjectNode obj) {
        JsonNode commandNode = obj.get("command");
        if (commandNode == null || !commandNode.isTextual()) {
            return;
        }
        String command = commandNode.asText().trim();
        if (command.startsWith("{") && command.contains("\"command\"")) {
            try {
                JsonNode nested = objectMapper.readTree(command);
                if (nested.has("command")) {
                    obj.put("command", nested.get("command").asText());
                }
            } catch (Exception ignored) {
                // keep original
            }
        }
    }

    private void fillWorkingDirIfAbsent(ObjectNode obj) {
        if (obj.hasNonNull("workingDir") && StringUtils.hasText(obj.get("workingDir").asText())) {
            return;
        }
        CodingSessionContext.Context ctx = CodingSessionContext.get();
        if (ctx == null) {
            return;
        }
        CodingTask task = codingTaskService.getActiveTask(ctx.sessionId());
        if (task == null) {
            return;
        }
        try {
            String workspace = codingWorkspaceService.resolveForTask(task).toString();
            obj.put("workingDir", workspace);
            log.debug("MCP shell 自动注入 workingDir={}", workspace);
        } catch (Exception e) {
            log.warn("无法解析 Coding 工作区，跳过 workingDir 注入: {}", e.getMessage());
        }
    }

    /**
     * Windows cmd.exe 对 dir "绝对路径" 与冗余 cd /d 易失败；在 Java 层预规范化命令文本。
     */
    private void normalizeWindowsShellCommand(ObjectNode obj) {
        if (!obj.hasNonNull("command") || !obj.hasNonNull("workingDir")) {
            return;
        }
        String command = obj.get("command").asText().trim();
        String workingDir = obj.get("workingDir").asText().trim();
        if (command.isEmpty() || workingDir.isEmpty()) {
            return;
        }
        String normalized = command;
        String wd = workingDir.replace('/', '\\');
        String cdPrefix = "cd /d \"" + wd + "\" && ";
        if (normalized.regionMatches(true, 0, cdPrefix, 0, cdPrefix.length())) {
            normalized = normalized.substring(cdPrefix.length()).trim();
        }
        String dirQuoted = "dir \"" + wd;
        if (normalized.regionMatches(true, 0, "dir \"", 0, 5)
                && normalized.toLowerCase().startsWith(dirQuoted.toLowerCase())) {
            int end = normalized.lastIndexOf('"');
            if (end > 5) {
                String absPath = normalized.substring(5, end).replace('/', '\\');
                if (absPath.toLowerCase().startsWith(wd.toLowerCase())) {
                    String rel = absPath.substring(wd.length()).replaceFirst("^\\\\", "");
                    if (!rel.isBlank()) {
                        normalized = "dir " + rel;
                    }
                }
            }
        }
        var readFs = Pattern.compile("readFileSync\\s*\\(\\s*['\"]([^'\"]+)['\"]",
                Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (Pattern.compile("node\\s+-e\\b", Pattern.CASE_INSENSITIVE).matcher(normalized).find()
                && readFs.find()) {
            String file = readFs.group(1);
            if (file.toLowerCase().endsWith(".js")
                    && Pattern.compile("new\\s+Function|SyntaxError|syntax", Pattern.CASE_INSENSITIVE)
                    .matcher(normalized).find()) {
                normalized = "node --check " + file.replace('/', '\\');
            } else if (!file.toLowerCase().endsWith(".js")) {
                normalized = "type " + file.replace('/', '\\');
            }
        }
        normalized = normalizeNodeCheckCommand(normalized, wd);
        normalized = stripShellRedirects(normalized);
        if (!normalized.equals(command)) {
            obj.put("command", normalized);
            log.debug("MCP shell 命令已规范化: {} -> {}", command, normalized);
        }
    }

    private String normalizeNodeCheckCommand(String command, String workingDir) {
        var m = Pattern.compile("node\\s+--check\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                .matcher(command);
        if (m.find()) {
            return relativizeNodeCheckPath(command, m.group(1), workingDir, m);
        }
        m = Pattern.compile("node\\s+--check\\s+([A-Za-z]:[^\\s]+)", Pattern.CASE_INSENSITIVE)
                .matcher(command);
        if (m.find()) {
            return relativizeNodeCheckPath(command, m.group(1), workingDir, m);
        }
        return command;
    }

    private String relativizeNodeCheckPath(String command, String absPath, String workingDir,
                                           java.util.regex.Matcher matcher) {
        String p = absPath.replace('/', '\\');
        String wd = workingDir.replace('/', '\\');
        if (!p.toLowerCase().startsWith(wd.toLowerCase())) {
            return command;
        }
        String rel = p.substring(wd.length()).replaceFirst("^\\\\", "");
        if (rel.isBlank()) {
            return command;
        }
        if (rel.toLowerCase().endsWith(".html")) {
            return "dir " + rel;
        }
        return matcher.replaceFirst("node --check " + Matcher.quoteReplacement(rel));
    }

    private static String stripShellRedirects(String command) {
        return command.replaceAll("\\s+2>&1\\s*$", "").trim();
    }

    private String wrapBareCommand(String raw) {
        try {
            ObjectNode obj = objectMapper.createObjectNode();
            obj.put("command", raw.trim());
            fillWorkingDirIfAbsent(obj);
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return raw;
        }
    }
}
