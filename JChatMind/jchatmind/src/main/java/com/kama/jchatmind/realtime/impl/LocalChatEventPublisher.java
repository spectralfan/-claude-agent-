package com.kama.jchatmind.realtime.impl;

import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import com.kama.jchatmind.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.messaging.mode", havingValue = "local", matchIfMissing = true)
public class LocalChatEventPublisher implements ChatEventPublisher {

    private final SseService sseService;

    @Override
    public void publish(String chatSessionId, SseMessage message) {
        sseService.deliver(chatSessionId, message);
    }
}
