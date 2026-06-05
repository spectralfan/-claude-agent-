package com.kama.jchatmind.realtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventEnvelope {
    private String sessionId;
    /** SseMessage JSON */
    private String payloadJson;
}
