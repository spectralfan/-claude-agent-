package com.kama.jchatmind.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.rpc.ChatWebSocketHandler;
import com.kama.jchatmind.rpc.JsonRpcMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalChatEventPublisher implements ChatEventPublisher {

    private final ChatWebSocketHandler wsHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    public LocalChatEventPublisher(ChatWebSocketHandler wsHandler) { this.wsHandler = wsHandler; }

    public void publish(String chatSessionId, SseMessage message) {
        try {
            String method = "sse." + message.getType().name().toLowerCase();
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("sessionId", chatSessionId);
            wrapper.set("message", mapper.valueToTree(message));
            JsonRpcMessage notif = JsonRpcMessage.notification(method, wrapper);
            wsHandler.broadcast(notif);
        } catch (Exception e) {
            log.warn("WS publish failed(session={}, type={}): {}", chatSessionId, message.getType(), e.getMessage());
        }
    }
}