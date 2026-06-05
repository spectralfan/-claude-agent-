package com.kama.jchatmind.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import com.kama.jchatmind.realtime.impl.RocketMqChatEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RealtimeMessagingConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "realtime.messaging.mode", havingValue = "rocketmq")
    ChatEventPublisher rocketMqChatEventPublisher(RocketMQTemplate rocketMQTemplate,
                                                  ObjectMapper objectMapper,
                                                  RealtimeMessagingProperties properties) {
        return new RocketMqChatEventPublisher(rocketMQTemplate, objectMapper, properties);
    }
}
