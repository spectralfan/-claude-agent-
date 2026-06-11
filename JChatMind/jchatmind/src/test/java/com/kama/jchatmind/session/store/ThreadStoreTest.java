package com.kama.jchatmind.session.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThreadStoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-12T10:00:00Z"), ZoneId.of("UTC"));

    @TempDir
    Path tempDir;

    private ThreadStore store;

    @BeforeEach
    void setUp() {
        store = new ThreadStore(tempDir, FIXED_CLOCK);
    }

    @Test
    void appendMessage_shouldCreateFile() {
        store.appendMessage("session-1", "user", "Hello", "run-1");
        assertTrue(store.exists("session-1"));
        List<ObjectNode> messages = store.readMessages("session-1");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role").asText());
        assertEquals("Hello", messages.get(0).get("content").asText());
    }

    @Test
    void appendMessage_withContentBlock_shouldWriteCorrectly() {
        ArrayNode blocks = MAPPER.createArrayNode();
        ObjectNode textBlock = MAPPER.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", "Let me check the code");
        blocks.add(textBlock);

        store.appendMessage("session-1", "assistant", blocks, "run-1");

        List<ObjectNode> messages = store.readMessages("session-1");
        assertEquals(1, messages.size());
        assertEquals("assistant", messages.get(0).get("role").asText());
        assertTrue(messages.get(0).get("content").isArray());
        assertEquals("text", messages.get(0).get("content").get(0).get("type").asText());
    }

    @Test
    void appendMessages_shouldBatchWrite() {
        ObjectNode msg1 = MAPPER.createObjectNode();
        msg1.put("role", "user");
        msg1.put("content", "First");
        ObjectNode msg2 = MAPPER.createObjectNode();
        msg2.put("role", "assistant");
        msg2.put("content", "Second");
        store.appendMessages("session-1", List.of(msg1, msg2), "run-1");

        List<ObjectNode> messages = store.readMessages("session-1");
        assertEquals(2, messages.size());
        assertEquals("First", messages.get(0).get("content").asText());
        assertEquals("Second", messages.get(1).get("content").asText());
    }

    @Test
    void readMessages_emptyFile_shouldReturnEmpty() {
        List<ObjectNode> messages = store.readMessages("nonexistent");
        assertTrue(messages.isEmpty());
    }

    @Test
    void readMessages_shouldTrimOrphanToolUse() {
        ArrayNode assistantBlocks = MAPPER.createArrayNode();
        ObjectNode toolUse = MAPPER.createObjectNode();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_abc");
        toolUse.put("name", "bash");
        toolUse.put("input", "ls");
        assistantBlocks.add(toolUse);
        store.appendMessage("session-1", "assistant", assistantBlocks, "run-1");
        store.appendMessage("session-1", "user", "Orphaned after tool_use", "run-1");

        List<ObjectNode> messages = store.readMessages("session-1");
        assertTrue(messages.isEmpty() || messages.stream()
                .noneMatch(m -> "Orphaned after tool_use".equals(m.get("content").asText())));
    }

    @Test
    void readMessages_withBalancedToolUse_shouldKeepEverything() {
        ArrayNode assistantBlocks = MAPPER.createArrayNode();
        ObjectNode toolUse = MAPPER.createObjectNode();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_xyz");
        toolUse.put("name", "bash");
        toolUse.put("input", "ls");
        assistantBlocks.add(toolUse);
        store.appendMessage("session-1", "assistant", assistantBlocks, "run-1");

        ArrayNode userBlocks = MAPPER.createArrayNode();
        ObjectNode toolResult = MAPPER.createObjectNode();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", "toolu_xyz");
        toolResult.put("content", "result ok");
        userBlocks.add(toolResult);
        store.appendMessage("session-1", "user", userBlocks, "run-1");

        List<ObjectNode> messages = store.readMessages("session-1");
        assertEquals(2, messages.size());
    }

    @Test
    void writeCompacted_shouldBackupAndReplace() {
        store.appendMessage("session-1", "user", "Original", "run-1");

        ObjectNode compacted = MAPPER.createObjectNode();
        compacted.put("role", "user");
        compacted.put("content", "Compacted summary");
        store.writeCompacted("session-1", List.of(compacted));

        List<ObjectNode> messages = store.readMessages("session-1");
        assertEquals(1, messages.size());
        assertEquals("Compacted summary", messages.get(0).get("content").asText());
    }

    @Test
    void deleteThread_shouldRemoveFile() {
        store.appendMessage("session-1", "user", "Hello", "run-1");
        assertTrue(store.exists("session-1"));
        store.deleteThread("session-1");
        assertFalse(store.exists("session-1"));
    }

    @Test
    void trimOrphanToolUse_staticMethod() {
        ObjectNode validMsg = MAPPER.createObjectNode();
        validMsg.put("role", "user");
        validMsg.put("content", "Valid");

        ObjectNode orphanToolUse = MAPPER.createObjectNode();
        orphanToolUse.put("role", "assistant");
        ArrayNode blocks = MAPPER.createArrayNode();
        ObjectNode tu = MAPPER.createObjectNode();
        tu.put("type", "tool_use");
        tu.put("id", "toolu_orphan");
        tu.put("name", "bash");
        tu.put("input", "");
        blocks.add(tu);
        orphanToolUse.set("content", blocks);

        List<ObjectNode> result = ThreadStore.trimOrphanToolUse(List.of(validMsg, orphanToolUse));
        assertEquals(1, result.size());
        assertEquals("Valid", result.get(0).get("content").asText());
    }
}