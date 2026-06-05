package com.kama.jchatmind.realtime.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import com.kama.jchatmind.realtime.config.RealtimeMessagingProperties;
import com.kama.jchatmind.realtime.model.ChatEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
@RequiredArgsConstructor
public class RocketMqChatEventPublisher implements ChatEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeMessagingProperties properties;

    @Override
    public void publish(String chatSessionId, SseMessage message) {
        try {
            String payloadJson = objectMapper.writeValueAsString(message);
            ChatEventEnvelope envelope = ChatEventEnvelope.builder()
                    .sessionId(chatSessionId)
                    .payloadJson(payloadJson)
                    .build();
            String body = objectMapper.writeValueAsString(envelope);
            String destination = properties.getRocketmq().getTopic() + ":" + chatSessionId;
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(body).build());
        } catch (JsonProcessingException e) {
            log.warn("RocketMQ 发布 Chat 事件序列化失败(session={}): {}", chatSessionId, e.getMessage());
        } catch (Exception e) {
            log.warn("RocketMQ 发布 Chat 事件失败(session={}): {}", chatSessionId, e.getMessage());
        }
    }
}
