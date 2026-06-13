package com.kama.jchatmind.realtime;

import com.kama.jchatmind.message.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private final ChatEventPublisher chatEventPublisher;

    public void tryPublish(String sessionId, SseMessage message) {
        if (sessionId == null || message == null) return;
        try {
            chatEventPublisher.publish(sessionId, message);
        } catch (Exception e) {
            log.warn("SSE publish failed(session={}, type={}): {}", sessionId, message.getType(), e.getMessage());
        }
    }
}