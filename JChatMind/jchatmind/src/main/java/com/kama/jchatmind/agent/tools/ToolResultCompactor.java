package com.kama.jchatmind.agent.tools;

import com.kama.jchatmind.agent.config.AgentToolResultProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按工具名将 tool 返回压缩为更短摘要，用于 LLM 历史上下文与 Memory Hub 镜像。
 * DB 仍保留完整 responseData，仅推理加载时压缩。
 */
@Component
@RequiredArgsConstructor
public class ToolResultCompactor {

    private static final Pattern EXIT_CODE = Pattern.compile("exit\\s*code\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final AgentToolResultProperties properties;

    public String compact(String toolName, String responseData) {
        if (!properties.isEnabled() || !StringUtils.hasText(responseData)) {
            return responseData == null ? "" : responseData;
        }
        CompactionMode mode = resolveMode(toolName);
        if (mode == CompactionMode.FULL && responseData.length() > properties.getDefaultMaxChars()) {
            mode = CompactionMode.MAX_CHARS;
        }
        return switch (mode) {
            case STATUS_ONLY -> compactStatusOnly(responseData);
            case HEAD_TAIL -> compactHeadTail(responseData, properties.getHeadTailMaxChars());
            case MAX_CHARS -> compactMaxChars(responseData, properties.getDefaultMaxChars());
            case FULL -> responseData;
        };
    }

    private CompactionMode resolveMode(String toolName) {
        String name = toolName == null ? "" : toolName.trim();
        if (matchesAny(name, properties.getStatusOnlyTools())) {
            return CompactionMode.STATUS_ONLY;
        }
        if (matchesAny(name, properties.getHeadTailTools())) {
            return CompactionMode.HEAD_TAIL;
        }
        if (matchesAny(name, properties.getMaxCharsTools()) || name.startsWith("list_")) {
            return CompactionMode.MAX_CHARS;
        }
        return CompactionMode.FULL;
    }

    private boolean matchesAny(String toolName, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (toolName.startsWith(prefix)) {
                    return true;
                }
            } else if (toolName.equals(pattern) || toolName.equalsIgnoreCase(pattern)) {
                return true;
            }
        }
        return false;
    }

    String compactStatusOnly(String responseData) {
        String trimmed = responseData.trim();
        Matcher exitMatcher = EXIT_CODE.matcher(trimmed);
        if (exitMatcher.find()) {
            int code = Integer.parseInt(exitMatcher.group(1));
            if (code == 0) {
                return "OK";
            }
            String reason = firstMeaningfulLine(trimmed, exitMatcher.end());
            return "FAILED: " + (StringUtils.hasText(reason) ? reason : "exit code " + code);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("成功") || lower.startsWith("ok:") || lower.equals("ok")) {
            return "OK";
        }
        if (trimmed.startsWith("错误") || lower.startsWith("failed") || lower.contains("error:")) {
            return "FAILED: " + firstLine(trimmed);
        }
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return compactHeadTail(trimmed, 200);
    }

    static String compactHeadTail(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int half = maxChars / 2;
        return text.substring(0, half) + "\n...(输出已截断)...\n" + text.substring(text.length() - half);
    }

    static String compactMaxChars(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated, total=" + text.length() + "]";
    }

    private static String firstMeaningfulLine(String text, int start) {
        if (start >= text.length()) {
            return "";
        }
        String rest = text.substring(start).trim();
        if (rest.startsWith("\n")) {
            rest = rest.substring(1).trim();
        }
        return firstLine(rest);
    }

    private static String firstLine(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text.trim() : text.substring(0, nl).trim();
    }

    private enum CompactionMode {
        STATUS_ONLY,
        HEAD_TAIL,
        MAX_CHARS,
        FULL
    }
}
