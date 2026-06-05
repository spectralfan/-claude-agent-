package com.kama.jchatmind.realtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 实时消息推送：local 直推 SSE，rocketmq 经 MQ 再桥接到 SSE。
 */
@Data
@Component
@ConfigurationProperties(prefix = "realtime.messaging")
public class RealtimeMessagingProperties {

    /** local | rocketmq */
    private String mode = "local";

    private RocketMq rocketmq = new RocketMq();

    @Data
    public static class RocketMq {
        private String nameServer = "127.0.0.1:9876";
        private String topic = "jchatmind-chat-events";
        private String producerGroup = "jchatmind-chat-producer";
        /** 广播模式：多实例各自消费，仅本机有 SSE 连接时投递 */
        private String consumerGroup = "jchatmind-sse-bridge";
    }

    public boolean isRocketMqMode() {
        return "rocketmq".equalsIgnoreCase(mode);
    }
}
