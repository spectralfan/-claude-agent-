package com.kama.jchatmind.session.loop;

import com.kama.jchatmind.session.ExecutionContext;
import com.kama.jchatmind.session.RunOutcome;
import com.kama.jchatmind.session.compact.CompactProperties;
import com.kama.jchatmind.session.compact.ContextCompactor;
import com.kama.jchatmind.session.event.EventBus;
import com.kama.jchatmind.session.event.StepFinishedEvent;
import com.kama.jchatmind.session.event.StepStartedEvent;
import com.kama.jchatmind.session.event.ToolCalledEvent;
import com.kama.jchatmind.session.event.ToolResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final EventBus eventBus;
    private final ContextCompactor compactor;
    private final CompactProperties compactProperties;
    private final ChatOptions chatOptions;
    private Path sessionDir;

    public AgentLoop(@Qualifier("deepseek-chat") ChatClient cc, ToolCallingManager tcm, EventBus eb,
                     ContextCompactor compactor, CompactProperties cp) {
        this.chatClient = cc; this.toolCallingManager = tcm; this.eventBus = eb;
        this.compactor = compactor; this.compactProperties = cp;
        this.chatOptions = DefaultToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();
    }

    public void setSessionDir(Path sessionDir) { this.sessionDir = sessionDir; }

    public RunOutcome run(ExecutionContext ctx, List<ToolCallback> toolCallbacks) {
        String runId = ctx.getRunId();

        while (!ctx.isDone()) {
            int step = ctx.getStep() + 1;
            ctx.setStep(step);
            eventBus.publish(new StepStartedEvent(runId, step, now()));

            String systemPrompt = buildSystemPrompt(ctx);
            Prompt prompt = Prompt.builder()
                    .messages(ctx.getMessages())
                    .chatOptions(this.chatOptions)
                    .build();

            ChatResponse response;
            try {
                response = chatClient.prompt(prompt)
                        .system(systemPrompt)
                        .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                        .call()
                        .chatClientResponse()
                        .chatResponse();
            } catch (Exception e) {
                log.error("LLM call failed runId={} step={}: {}", runId, step, e.getMessage());
                ctx.markFailed("llm_error"); break;
            }

            // Track context usage
            if (response.getMetadata() != null && response.getMetadata().containsKey("usage")) {
                try {
                    var usage = response.getMetadata().get("usage");
                } catch (Exception ignored) {}
            }

            AssistantMessage output = response.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            ctx.getMessages().add(output);

            if (toolCalls == null || toolCalls.isEmpty()) {
                ctx.setResult(output.getText() != null ? output.getText() : "");
                ctx.markSuccess();
                eventBus.publish(new StepFinishedEvent(runId, step, now(), "success"));
                break;
            }

            for (AssistantMessage.ToolCall tc : toolCalls) {
                eventBus.publish(new ToolCalledEvent(runId, tc.name(), tc.arguments(), step));
            }

            try {
                Prompt actPrompt = Prompt.builder().messages(ctx.getMessages()).chatOptions(this.chatOptions).build();
                ToolExecutionResult result = toolCallingManager.executeToolCalls(actPrompt, response);
                ToolResponseMessage toolResponse = (ToolResponseMessage) result.conversationHistory()
                        .get(result.conversationHistory().size() - 1);
                for (ToolResponseMessage.ToolResponse tr : toolResponse.getResponses()) {
                    eventBus.publish(new ToolResultEvent(runId, tr.name(), tr.responseData(), false, step));
                }
                ctx.getMessages().clear();
                ctx.getMessages().addAll(result.conversationHistory());
            } catch (Exception e) {
                log.warn("Tool execution error runId={} step={}: {}", runId, step, e.getMessage());
                ctx.markFailed("tool_error"); break;
            }

            if (ctx.getStep() >= ctx.getMaxSteps()) {
                ctx.markFailed("exceeded_max_steps");
                eventBus.publish(new StepFinishedEvent(runId, step, now(), "failed")); break;
            }

            eventBus.publish(new StepFinishedEvent(runId, step, now(), "running"));

            // Context compaction check — KamaClaude style
            if (!ctx.isDone() && toolCalls != null && !toolCalls.isEmpty()
                    && compactProperties.isEnabled() && ctx.getContextPct() >= compactProperties.getThreshold()
                    && sessionDir != null) {
                compactor.compact(ctx, sessionDir);
            }
        }

        return new RunOutcome(ctx.getStatus(), ctx.getResult(), ctx.getReason(), ctx.getStep());
    }

    private String buildSystemPrompt(ExecutionContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.getSystemPromptOverride() != null && !ctx.getSystemPromptOverride().isBlank()) {
            sb.append(ctx.getSystemPromptOverride());
        } else {
            sb.append("You are a helpful AI assistant. Use the available tools to complete the goal.");
        }
        if (ctx.getSessionNotes() != null && !ctx.getSessionNotes().isBlank()) {
            sb.append("\n\n## Session Notes\n").append(ctx.getSessionNotes());
        }
        return sb.toString();
    }

    private static String now() { return Instant.now().toString(); }
}