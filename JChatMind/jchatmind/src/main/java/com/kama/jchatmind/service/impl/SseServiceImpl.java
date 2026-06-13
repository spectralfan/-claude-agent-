package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.rpc.ChatWebSocketHandler;
import com.kama.jchatmind.rpc.JsonRpcMessage;
import com.kama.jchatmind.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    private final ChatWebSocketHandler wsHandler;
    public SseServiceImpl(ChatWebSocketHandler wsHandler) { this.wsHandler = wsHandler; }

    public boolean deliver(String chatSessionId, SseMessage message) {
        try {
            String method = "sse." + message.getType().name().toLowerCase();
            JsonRpcMessage notif = JsonRpcMessage.notification(method, message);
            wsHandler.broadcast(notif);
            return true;
        } catch (Exception e) {
            log.warn("WS deliver failed(session={}, type={}): {}", chatSessionId, message.getType(), e.getMessage());
            return false;
        }
    }
}