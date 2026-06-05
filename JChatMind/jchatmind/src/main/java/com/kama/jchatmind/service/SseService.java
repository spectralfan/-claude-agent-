package com.kama.jchatmind.service;

import com.kama.jchatmind.message.SseMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseService {
    // 没有用户系统，使用 chatSessionId 作为连接标识
    SseEmitter connect(String chatSessionId);

    /**
     * 尽力投递到本机 SSE 连接，无连接或失败时不抛异常。
     *
     * @return 是否成功写入 emitter
     */
    boolean deliver(String chatSessionId, SseMessage message);
}
