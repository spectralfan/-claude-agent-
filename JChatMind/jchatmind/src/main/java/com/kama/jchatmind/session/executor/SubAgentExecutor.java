package com.kama.jchatmind.session.executor;

import com.kama.jchatmind.agent.profile.AgentProfile;
import com.kama.jchatmind.session.ExecutionContext;
import com.kama.jchatmind.session.RunOutcome;
import com.kama.jchatmind.session.SessionManager;
import com.kama.jchatmind.session.SessionRunIdGenerator;
import com.kama.jchatmind.session.loop.AgentLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SubAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(SubAgentExecutor.class);

    private final SessionManager sessionManager;
    private final AgentLoop agentLoop;

    public SubAgentExecutor(SessionManager sessionManager, AgentLoop agentLoop) {
        this.sessionManager = sessionManager;
        this.agentLoop = agentLoop;
    }

    public RunOutcome execute(String sessionId, String goal, AgentProfile profile, List<ToolCallback> tools) {
        String runId = SessionRunIdGenerator.newRunId();
        int maxSteps = profile != null ? profile.getMaxSteps() : 35;
        ExecutionContext ctx = new ExecutionContext(runId, goal, maxSteps);

        if (profile != null && profile.getSystemPrompt() != null) {
            ctx.setSystemPromptOverride(profile.getSystemPrompt());
        }
        String notes = sessionManager.getNoteStore().readNotes(sessionId);
        if (!notes.isEmpty()) {
            ctx.setSessionNotes(notes);
        }

        sessionManager.startRun(sessionId, goal);
        log.info("SubAgentExecutor started: session={} runId={} profile={}",
                sessionId, runId, profile != null ? profile.getName() : "default");

        RunOutcome outcome = agentLoop.run(ctx, tools);

        persistMessages(sessionId, ctx, runId);
        sessionManager.finishRun(sessionId, runId, outcome.getStatus(), outcome.getReason());

        log.info("SubAgentExecutor finished: session={} runId={} status={}",
                sessionId, runId, outcome.getStatus());
        return outcome;
    }

    public CompletableFuture<RunOutcome> executeAsync(String sessionId, String goal,
                                                       AgentProfile profile, List<ToolCallback> tools) {
        return CompletableFuture.supplyAsync(() -> execute(sessionId, goal, profile, tools));
    }

    private void persistMessages(String sessionId, ExecutionContext ctx, String runId) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<com.fasterxml.jackson.databind.node.ObjectNode> nodes = ctx.getMessages().stream()
                .map(msg -> {
                    com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
                    if (msg instanceof AssistantMessage am) {
                        node.put("role", "assistant");
                        node.put("content", am.getText() != null ? am.getText() : "");
                    } else if (msg instanceof ToolResponseMessage tr) {
                        node.put("role", "user");
                        node.put("content", tr.getResponses().stream()
                                .map(r -> r.responseData()).toList().toString());
                    } else {
                        node.put("role", "user");
                        node.put("content", msg.getText() != null ? msg.getText() : "");
                    }
                    return node;
                })
                .toList();
        sessionManager.getThreadStore().appendMessages(sessionId, nodes, runId);
    }
}