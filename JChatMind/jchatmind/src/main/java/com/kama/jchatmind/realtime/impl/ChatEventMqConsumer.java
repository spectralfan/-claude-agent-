package com.kama.jchatmind.realtime.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.realtime.model.ChatEventEnvelope;
import com.kama.jchatmind.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 广播消费：每个应用实例都收到消息，仅向本机已建立的 SSE 连接投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.messaging.mode", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${realtime.messaging.rocketmq.topic}",
        consumerGroup = "${realtime.messaging.rocketmq.consumer-group}",
        messageModel = MessageModel.BROADCASTING
)
public class ChatEventMqConsumer implements RocketMQListener<String> {

    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            ChatEventEnvelope envelope = objectMapper.readValue(message, ChatEventEnvelope.class);
            if (envelope.getSessionId() == null || envelope.getPayloadJson() == null) {
                return;
            }
            SseMessage sseMessage = objectMapper.readValue(envelope.getPayloadJson(), SseMessage.class);
            boolean delivered = sseService.deliver(envelope.getSessionId(), sseMessage);
            if (delivered) {
                log.debug("MQ→SSE 已投递 session={} type={}", envelope.getSessionId(), sseMessage.getType());
            }
        } catch (Exception e) {
            log.warn("MQ→SSE 桥接失败: {}", e.getMessage());
        }
    }
}
