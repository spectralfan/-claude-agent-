package com.kama.jchatmind.agent.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolAwareMessageWindowChatMemoryTest {

    private static final String SESSION = "test-session";

    @Test
    void trimToWindow_manyToolRounds_shouldNotOrphanToolMessages() {
        ToolAwareMessageWindowChatMemory memory = ToolAwareMessageWindowChatMemory.builder()
                .maxMessages(10)
                .pinFirstUserMessage(true)
                .pinSystemMessage(true)
                .build();

        List<Message> seed = new ArrayList<>();
        seed.add(new UserMessage("build jump game"));
        for (int i = 0; i < 15; i++) {
            seed.add(assistantWithTools("call-" + i, "run_terminal_cmd"));
            seed.add(toolResponse("call-" + i, "run_terminal_cmd", "ok " + i));
        }
        seed.add(new SystemMessage("system rules"));

        memory.add(SESSION, seed);
        List<Message> kept = memory.get(SESSION);

        assertTrue(kept.size() <= 10);
        assertTrue(kept.get(0) instanceof UserMessage);
        assertTrue(kept.stream().anyMatch(m -> m instanceof SystemMessage));
        assertNoOrphanToolMessages(kept);
    }

    @Test
    void trimToWindow_parallelTools_shouldTrimWholeRound() {
        List<Message> messages = List.of(
                new UserMessage("goal"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(
                                new AssistantMessage.ToolCall("c1", "function", "t1", "{}"),
                                new AssistantMessage.ToolCall("c2", "function", "t2", "{}"),
                                new AssistantMessage.ToolCall("c3", "function", "t3", "{}")
                        ))
                        .build(),
                toolResponse("c1", "t1", "r1"),
                toolResponse("c2", "t2", "r2"),
                toolResponse("c3", "t3", "r3"),
                new AssistantMessage("done")
        );

        ToolAwareMessageWindowChatMemory memory = ToolAwareMessageWindowChatMemory.builder()
                .maxMessages(4)
                .build();

        List<Message> kept = memory.trimToWindow(new ArrayList<>(messages));

        assertTrue(kept.size() <= 4);
        assertNoOrphanToolMessages(kept);
    }

    @Test
    void clearAndBulkAdd_shouldBehaveLikeAct() {
        ToolAwareMessageWindowChatMemory memory = ToolAwareMessageWindowChatMemory.builder()
                .maxMessages(6)
                .pinFirstUserMessage(true)
                .build();

        memory.add(SESSION, List.of(new UserMessage("goal")));
        for (int i = 0; i < 5; i++) {
            memory.add(SESSION, List.of(
                    assistantWithTools("id-" + i, "tool"),
                    toolResponse("id-" + i, "tool", "out")
            ));
        }

        List<Message> history = memory.get(SESSION);
        memory.clear(SESSION);
        memory.add(SESSION, history);

        List<Message> after = memory.get(SESSION);
        assertEquals(history.size(), after.size());
        assertNoOrphanToolMessages(after);
    }

    @Test
    void partitionRoundIndices_shouldGroupAssistantAndTools() {
        List<Message> messages = List.of(
                new UserMessage("u"),
                assistantWithTools("a1", "tool"),
                toolResponse("a1", "tool", "r"),
                new AssistantMessage("text")
        );
        var pinned = java.util.Set.of(0);
        List<List<Integer>> rounds = ToolAwareMessageWindowChatMemory.partitionRoundIndices(messages, pinned);

        assertEquals(2, rounds.size());
        assertEquals(List.of(1, 2), rounds.get(0));
        assertEquals(List.of(3), rounds.get(1));
    }

    private static void assertNoOrphanToolMessages(List<Message> messages) {
        boolean pendingAssistant = false;
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistant
                    && assistant.getToolCalls() != null
                    && !assistant.getToolCalls().isEmpty()) {
                pendingAssistant = true;
            } else if (message instanceof ToolResponseMessage) {
                assertTrue(pendingAssistant, "悬空 tool 消息: " + message);
            } else {
                pendingAssistant = false;
            }
        }
    }

    private static AssistantMessage assistantWithTools(String id, String name) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, "{}")))
                .build();
    }

    private static ToolResponseMessage toolResponse(String id, String name, String data) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, data)))
                .build();
    }
}
