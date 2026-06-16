package com.kama.jchatmind.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonRpcDispatcher dispatcher;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(JsonRpcDispatcher dispatcher) { this.dispatcher = dispatcher; }

    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: {}", session.getId());
        // 检查是否需要重放事件
        String replayRunId = getQueryParam(session, "replay_run_id");
        if (replayRunId != null && !replayRunId.isBlank()) {
            replayEvents(session, replayRunId);
        }
    }

    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonRpcMessage req = mapper.readValue(message.getPayload(), JsonRpcMessage.class);
            JsonRpcMessage resp = dispatcher.dispatch(req);
            if (resp != null) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(resp)));
            }
        } catch (Exception e) {
            log.warn("WebSocket error: {}", e.getMessage());
            try {
                JsonRpcMessage err = JsonRpcMessage.error(null, -32700, "Parse error");
                String json = mapper.writeValueAsString(err);
                session.sendMessage(new TextMessage(json));
            } catch (Exception ignored) {}
        }
    }

    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {} status={}", session.getId(), status);
    }

    public void broadcast(JsonRpcMessage notification) {
        if (!notification.isNotification()) return;
        try {
            String json = mapper.writeValueAsString(notification);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession s : sessions.values()) {
                if (s.isOpen()) { try { s.sendMessage(msg); } catch (Exception e) { log.warn("Send failed: {}", e.getMessage()); } }
            }
        } catch (Exception e) { log.warn("Broadcast failed: {}", e.getMessage()); }
    }

    private String getQueryParam(WebSocketSession session, String name) {
        java.net.URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void replayEvents(WebSocketSession session, String runId) {
        Path eventsFile = Path.of(".jchatmind/sessions", "runs", runId, "events.jsonl");
        if (!Files.exists(eventsFile)) {
            log.info("No events file for replay: {}", runId);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(eventsFile);
            for (String line : lines) {
                if (!session.isOpen()) break;
                JsonRpcMessage notification = JsonRpcMessage.notification("event.replay",
                        mapper.readTree(line));
                session.sendMessage(new TextMessage(mapper.writeValueAsString(notification)));
            }
            log.info("Replayed {} events for runId={}", lines.size(), runId);
        } catch (Exception e) {
            log.warn("Replay failed for runId={}: {}", runId, e.getMessage());
        }
    }
}