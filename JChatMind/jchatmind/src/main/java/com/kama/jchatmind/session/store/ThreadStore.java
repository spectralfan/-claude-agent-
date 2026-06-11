package com.kama.jchatmind.session.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thread.jsonl 消息流文件持久化存储。
 *
 * <p>每条消息为 JSONL 一行，包含 ts / role / content / run_id 字段。
 * 该文件是消息的唯一权威源，DB 仅作为索引。</p>
 *
 * <p>文件位置：{sessionRoot}/{sessionId}/thread.jsonl</p>
 */
public class ThreadStore {

    private static final Logger log = LoggerFactory.getLogger(ThreadStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path sessionRoot;
    private final Clock clock;

    public ThreadStore(Path sessionRoot) {
        this(sessionRoot, Clock.systemUTC());
    }

    ThreadStore(Path sessionRoot, Clock clock) {
        this.sessionRoot = sessionRoot;
        this.clock = clock;
    }

    /**
     * 追加一条消息到 thread.jsonl。
     */
    public void appendMessage(String sessionId, String role, Object content, String runId) {
        ObjectNode row = MAPPER.createObjectNode();
        row.put("ts", Instant.now(clock).toString());
        row.put("role", role);
        row.set("content", MAPPER.valueToTree(content));
        if (runId != null) {
            row.put("run_id", runId);
        }
        appendJson(sessionId, row);
    }

    /**
     * 批量追加多条消息到 thread.jsonl。
     */
    public void appendMessages(String sessionId, List<ObjectNode> messages, String runId) {
        Path file = resolveThreadFile(sessionId);
        ensureDir(file.getParent());
        try {
            StringBuilder sb = new StringBuilder();
            for (ObjectNode msg : messages) {
                ObjectNode row = MAPPER.createObjectNode();
                row.put("ts", Instant.now(clock).toString());
                row.put("role", msg.has("role") ? msg.get("role").asText() : "user");
                row.set("content", msg.get("content"));
                if (msg.has("run_id") && !msg.get("run_id").isNull()) {
                    row.set("run_id", msg.get("run_id"));
                } else if (runId != null) {
                    row.put("run_id", runId);
                }
                sb.append(MAPPER.writeValueAsString(row)).append("\n");
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append messages to thread.jsonl for session={}", sessionId, e);
        }
    }

    /**
     * 从 thread.jsonl 读取完整消息列表，返回 Anthropic API 兼容格式。
     *
     * <p>会自动裁掉尾部未配对的 tool_use 块。</p>
     */
    public List<ObjectNode> readMessages(String sessionId) {
        Path file = resolveThreadFile(sessionId);
        if (!Files.exists(file)) {
            return List.of();
        }

        List<ObjectNode> messages = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                try {
                    JsonNode row = MAPPER.readTree(line);
                    String role = row.has("role") ? row.get("role").asText() : null;
                    if (role == null || (!role.equals("user") && !role.equals("assistant"))) {
                        log.warn("skip unknown thread role session={} line={} role={}", sessionId, i + 1, role);
                        continue;
                    }
                    ObjectNode msg = MAPPER.createObjectNode();
                    msg.put("role", role);
                    msg.set("content", row.get("content"));
                    messages.add(msg);
                } catch (JsonProcessingException e) {
                    log.warn("skip broken thread row session={} line={}", sessionId, i + 1);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read thread.jsonl for session={}", sessionId, e);
            return List.of();
        }

        return trimOrphanToolUse(messages);
    }

    /**
     * 压缩后覆盖写入 thread.jsonl，旧文件自动备份为 thread_{ts}.jsonl.bak。
     */
    public void writeCompacted(String sessionId, List<ObjectNode> messages) {
        Path file = resolveThreadFile(sessionId);
        if (Files.exists(file)) {
            String ts = Instant.now(clock).toString().replaceAll("[:\\\\-]", "").substring(0, 15);
            Path bak = file.resolveSibling("thread_" + ts + ".jsonl.bak");
            try {
                Files.move(file, bak);
                log.info("Backed up thread.jsonl to {} for session={}", bak.getFileName(), sessionId);
            } catch (IOException e) {
                log.warn("Failed to backup thread.jsonl for session={}", sessionId, e);
            }
        }

        try {
            ensureDir(file.getParent());
            StringBuilder sb = new StringBuilder();
            for (ObjectNode msg : messages) {
                ObjectNode row = MAPPER.createObjectNode();
                row.put("ts", Instant.now(clock).toString());
                row.put("role", msg.get("role").asText());
                row.set("content", msg.get("content"));
                sb.append(MAPPER.writeValueAsString(row)).append("\n");
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            log.info("Compacted thread.jsonl written for session={} ({} messages)", sessionId, messages.size());
        } catch (IOException e) {
            log.error("Failed to write compacted thread.jsonl for session={}", sessionId, e);
        }
    }

    /**
     * 删除 thread.jsonl 文件。
     */
    public void deleteThread(String sessionId) {
        Path file = resolveThreadFile(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete thread.jsonl for session={}", sessionId, e);
        }
    }

    /**
     * 检查 thread.jsonl 是否存在。
     */
    public boolean exists(String sessionId) {
        return Files.exists(resolveThreadFile(sessionId));
    }

    // ==================== Internal ====================

    Path resolveThreadFile(String sessionId) {
        return sessionRoot.resolve(sessionId).resolve("thread.jsonl");
    }

    private void appendJson(String sessionId, ObjectNode row) {
        Path file = resolveThreadFile(sessionId);
        ensureDir(file.getParent());
        try {
            Files.writeString(file, MAPPER.writeValueAsString(row) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append to thread.jsonl for session={}", sessionId, e);
        }
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + dir, e);
        }
    }

    /**
     * 裁掉尾部未配对 tool_use 及其后续消息，避免 Anthropic API 的 messages.invalid 错误。
     */
    static List<ObjectNode> trimOrphanToolUse(List<ObjectNode> messages) {
        Set<String> pending = new HashSet<>();
        int lastBalanced = 0;
        for (int i = 0; i < messages.size(); i++) {
            ObjectNode msg = messages.get(i);
            String role = msg.get("role").asText();
            JsonNode content = msg.get("content");

            if ("assistant".equals(role) && content != null && content.isArray()) {
                for (JsonNode block : content) {
                    if ("tool_use".equals(block.get("type").asText())) {
                        pending.add(block.get("id").asText());
                    }
                }
            } else if ("user".equals(role) && content != null && content.isArray()) {
                for (JsonNode block : content) {
                    if ("tool_result".equals(block.get("type").asText())) {
                        pending.remove(block.get("tool_use_id").asText());
                    }
                }
            }

            if (pending.isEmpty()) {
                lastBalanced = i + 1;
            }
        }

        if (!pending.isEmpty()) {
            log.warn("trimming {} orphan tool_use blocks, keeping {} of {} messages",
                    pending.size(), lastBalanced, messages.size());
            return messages.subList(0, lastBalanced);
        }
        return messages;
    }
}