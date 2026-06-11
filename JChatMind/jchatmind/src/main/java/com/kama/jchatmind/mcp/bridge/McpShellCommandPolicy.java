package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MCP shell 命令策略门禁：拦截已知高危写法，引导 Agent 使用 {@code coding_verify_tools}。
 * 与 command-runner.mjs 双层防护，避免每次跑完才发现引号/端口问题。
 */
@Component
public class McpShellCommandPolicy {

    public static final String POLICY_VERSION = "1.1";

    private static final List<Pattern> BLOCKED = List.of(
            Pattern.compile("node\\s+--input-type=module", Pattern.CASE_INSENSITIVE),
            Pattern.compile("node\\s+-e\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bhttp-server\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpython\\s+-m\\s+http\\.server\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnpx\\s+serve\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bstart\\s+https?://", Pattern.CASE_INSENSITIVE),
            Pattern.compile("-p\\s+8080\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("localhost:8080", Pattern.CASE_INSENSITIVE),
            Pattern.compile("node\\s+--check\\s+\\S+\\.html\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[|]|>nul|(^|[^&])&[^&]", Pattern.CASE_INSENSITIVE)
    );

    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    public McpShellCommandPolicy(ObjectMapper objectMapper, McpProperties mcpProperties) {
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    /**
     * @return 若应拒绝执行，返回给 Agent 的中文说明；否则 empty
     */
    public Optional<String> rejectReason(String toolInput) {
        if (!mcpProperties.getShell().isPolicyEnabled()) {
            return Optional.empty();
        }
        String command = extractCommand(toolInput);
        if (command.isBlank()) {
            return Optional.empty();
        }
        String lower = command.toLowerCase(Locale.ROOT);
        for (Pattern p : BLOCKED) {
            if (p.matcher(command).find()) {
                return Optional.of(buildBlockedMessage(p.pattern(), command));
            }
        }
        if (lower.contains("import ") && lower.contains("from '") && lower.contains("node")) {
            return Optional.of("""
                    禁止经 MCP 执行 ES module 多行 import 脚本（引号会被 shell 弄碎）。
                    请改用 coding_verify_tools：
                    - list_stack_verify_commands 后 run_stack_verify(label)
                    - check_js_syntax(relativePath) 仅 .js 语法检查
                    - verify_coding_file(relativePath) 确认 HTML 等文件存在
                    exit code: 1""");
        }
        return Optional.empty();
    }

    private String buildBlockedMessage(String pattern, String command) {
        String hint = switch (pattern) {
            case "node\\s+-e\\b" -> "用 list_stack_verify_commands + run_stack_verify(label)，或 check_js_syntax(仅.js)";
            case "node\\s+--input-type=module" -> "用 check_js_syntax 逐个检查 .js 文件";
            case "\\bhttp-server\\b", "\\bpython\\s+-m\\s+http\\.server\\b" ->
                    "HTML 预览请用户浏览器直接打开文件；静态服务用 preview-port(5500) 在本地终端手动启动";
            case "-p\\s+8080\\b", "localhost:8080" -> "8080 为 JChatMind 后端端口，请用 coding.workspace.preview-port(默认5500)";
            case "node\\s+--check\\s+\\S+\\.html\\b" ->
                    "HTML 不能用 node --check；请用 verify_coding_file(path) 或 dir/type 确认文件存在";
            case "[|]|>nul|(^|[^&])&[^&]" ->
                    "禁止 cmd 管道/重定向（|、&、>nul）；用 list_stack_verify_commands + run_stack_verify(label)";
            default -> "请用 list_stack_verify_commands + run_stack_verify(label)";
        };
        return """
                MCP 命令被策略拦截（%s）。
                原命令: %s
                建议: %s
                exit code: 1""".formatted(pattern, truncate(command, 200), hint);
    }

    private String extractCommand(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return "";
        }
        String trimmed = toolInput.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root.has("command")) {
                    return root.get("command").asText("").trim();
                }
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
