package com.kama.jchatmind.realtime;

import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.impl.LocalChatEventPublisher;
import com.kama.jchatmind.service.SseService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LocalChatEventPublisherTest {

    @Test
    void publish_shouldDelegateToSseDeliver() {
        SseService sseService = mock(SseService.class);
        ChatEventPublisher publisher = new LocalChatEventPublisher(sseService);
        SseMessage message = SseMessage.builder()
                .type(SseMessage.Type.CODING_STARTED)
                .payload(SseMessage.Payload.builder().statusText("ok").build())
                .build();

        publisher.publish("s1", message);

        verify(sseService).deliver(eq("s1"), eq(message));
    }
}
