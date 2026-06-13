package com.kama.jchatmind.session.compact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextCompactorTest {

    @TempDir Path tempDir;

    @Test void messagesToText_shouldFormatMessages() {
        List<Message> msgs = List.of(
                new UserMessage("Hello"),
                new AssistantMessage("Hi there")
        );
        String text = ContextCompactor.messagesToText(msgs);
        assertTrue(text.contains("[USER]"));
        assertTrue(text.contains("Hello"));
        assertTrue(text.contains("[ASSISTANT]"));
        assertTrue(text.contains("Hi there"));
    }

    @Test void messagesToText_withToolResponse() {
        var tr = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("bash", "output", "")))
                .build();
        String text = ContextCompactor.messagesToText(List.of(tr));
        assertTrue(text.contains("[TOOL]"));
    }

    @Test void messagesToText_emptyList() {
        assertEquals("", ContextCompactor.messagesToText(List.of()));
    }

    @Test void messagesToText_withEmptyText() {
        List<Message> msgs = new ArrayList<>();
        msgs.add(new AssistantMessage(""));
        String text = ContextCompactor.messagesToText(msgs);
        assertNotNull(text);
    }
}