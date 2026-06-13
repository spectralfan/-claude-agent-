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
}