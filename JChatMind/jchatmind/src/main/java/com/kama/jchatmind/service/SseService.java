package com.kama.jchatmind.service;
import com.kama.jchatmind.message.SseMessage;

public interface SseService {
    boolean deliver(String chatSessionId, SseMessage message);
}