package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class McpShellCommandPolicy {

    private static final Logger log = LoggerFactory.getLogger(McpShellCommandPolicy.class);

    private final ObjectMapper objectMapper;
    private final McpProperties mcpProperties;

    public McpShellCommandPolicy(ObjectMapper objectMapper, McpProperties mcpProperties) {
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    public Optional<String> rejectReason(String toolInput) {
        if (!mcpProperties.getShell().isPolicyEnabled()) return Optional.empty();
        String command = extractCommand(toolInput);
        if (command.isBlank()) return Optional.empty();

        if (command.contains("node -e") || command.contains("node --input-type=module")) {
            log.warn("建议用 coding_verify_tools 替代裸 node -e: {}", command);
        }
        if (command.contains("http-server") || command.contains("python -m http.server")) {
            log.warn("预览请用 preview-port 5500，而非启动 HTTP 服务器: {}", command);
        }
        if (command.contains(":8080") || command.contains("-p 8080")) {
            log.warn("端口 8080 为后端保留，请勿占用: {}", command);
        }
        return Optional.empty();
    }

    private String extractCommand(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) return "";
        String trimmed = toolInput.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                if (root.has("command")) return root.get("command").asText("").trim();
            } catch (Exception ignored) {}
        }
        return trimmed;
    }
}