package com.kama.jchatmind.session.compact;

import com.kama.jchatmind.session.ExecutionContext;
import com.kama.jchatmind.session.event.ContextCompactedEvent;
import com.kama.jchatmind.session.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextCompactor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompactor.class);
    private static final String COMPACT_PROMPT = 
        "You are compressing an agent conversation into a handoff summary.\\n"
        + "Another LLM instance will continue this task from your summary alone.\\n\\n"
        + "Structure your response with exactly these sections:\\n"
        + "## 1. Original Goal\\n"
        + "## 2. Completed Steps (bullet list, be specific)\\n"
        + "## 3. Key Constraints & Discoveries\\n"
        + "## 4. Current File State\\n"
        + "## 5. Remaining TODOs\\n"
        + "## 6. Critical Data (values the next LLM needs verbatim)\\n\\n"
        + "Be concise. Omit reasoning steps and intermediate attempts.";

    private final ChatClient chatClient;
    private final EventBus eventBus;
    private final CompactProperties properties;

    public ContextCompactor(@Qualifier("deepseek-chat") ChatClient cc, EventBus eb, CompactProperties props) {
        this.chatClient = cc; this.eventBus = eb; this.properties = props;
    }

    public void compact(ExecutionContext ctx, Path sessionDir) {
        if (!properties.isEnabled()) return;
        try {
            CompactionResult result = compactMessages(ctx.getMessages());
            if (result == null) return;
            ctx.getMessages().clear();
            ctx.getMessages().add(new UserMessage(result.getSummaryText()));
            ctx.getMessages().add(new AssistantMessage("Understood, I will continue from this summary."));
            writeSummary(sessionDir, result.getSummaryText());
            eventBus.publish(new ContextCompactedEvent(
                    "session", ctx.getRunId(), result.getOriginalTokenEstimate(), result.getSummaryTokens(), now()));
            log.info("Compacted session run={} original≈{} summary={} tokens",
                    ctx.getRunId(), result.getOriginalTokenEstimate(), result.getSummaryTokens());
        } catch (Exception e) { log.warn("Compaction failed: {}", e.getMessage()); }
    }

    CompactionResult compactMessages(List<Message> messages) {
        int originalEstimate = messages.stream()
                .mapToInt(m -> m.getText() != null ? m.getText().length() / 4 : 0)
                .sum();
        String historyText = messagesToText(messages);
        String response = chatClient.prompt()
                .system("You are a helpful assistant that summarizes conversations.")
                .user(COMPACT_PROMPT + "\\n\\n---\\n\\n" + historyText)
                .call()
                .content();
        if (response == null || response.isBlank()) return null;
        int summaryTokens = response.length() / 4;
        return new CompactionResult(response, originalEstimate, summaryTokens);
    }

    private void writeSummary(Path sessionDir, String text) {
        try {
            Files.createDirectories(sessionDir);
            String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC")).format(Instant.now());
            Files.writeString(sessionDir.resolve("summary_" + ts + ".md"), text, StandardCharsets.UTF_8);
        } catch (Exception e) { log.warn("Failed to write summary file: {}", e.getMessage()); }
    }

    static String messagesToText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String role;
            if (msg instanceof UserMessage) role = "USER";
            else if (msg instanceof AssistantMessage) role = "ASSISTANT";
            else if (msg instanceof ToolResponseMessage) role = "TOOL";
            else role = "SYSTEM";
            String text = msg.getText() != null ? msg.getText() : "";
            sb.append("[").append(role).append("]\\n").append(text).append("\\n\\n");
        }
        return sb.toString();
    }

    private static String now() { return Instant.now().toString(); }
}