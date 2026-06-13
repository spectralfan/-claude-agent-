package com.kama.jchatmind.realtime;
import com.kama.jchatmind.message.SseMessage;

public interface ChatEventPublisher {
    void publish(String chatSessionId, SseMessage message);
}