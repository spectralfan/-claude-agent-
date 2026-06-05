package com.kama.jchatmind.realtime;

import com.kama.jchatmind.message.SseMessage;

/**
 * 会话实时事件发布入口。浏览器仍通过 SSE 连接接收；rocketmq 模式下经 MQ 解耦后再桥接 SSE。
 */
public interface ChatEventPublisher {

    void publish(String chatSessionId, SseMessage message);
}
