package com.kama.jchatmind.realtime;

import com.kama.jchatmind.message.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SSE 推送是旁路通知：无连接或失败时不应让主流程失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeNotifier {

    private final ChatEventPublisher chatEventPublisher;

    public void tryPublish(String sessionId, SseMessage message) {
        if (sessionId == null || message == null) {
            return;
        }
        try {
            chatEventPublisher.publish(sessionId, message);
        } catch (Exception e) {
            log.warn("SSE 推送失败(session={}, type={}): {}", sessionId,
                    message.getType(), e.getMessage());
        }
    }
}
