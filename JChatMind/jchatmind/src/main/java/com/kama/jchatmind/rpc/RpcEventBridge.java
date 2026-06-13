package com.kama.jchatmind.rpc;

import com.kama.jchatmind.session.event.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RpcEventBridge {

    private static final Logger log = LoggerFactory.getLogger(RpcEventBridge.class);
    private final EventBus eventBus;
    private final ChatWebSocketHandler wsHandler;

    public RpcEventBridge(EventBus eventBus, ChatWebSocketHandler wsHandler) {
        this.eventBus = eventBus; this.wsHandler = wsHandler;
    }

    @PostConstruct
    public void init() {
        eventBus.subscribe(event -> {
            try { wsHandler.broadcast(toNotification(event)); }
            catch (Exception e) { log.warn("RpcEventBridge failed: {}", e.getMessage()); }
        });
        log.info("RpcEventBridge: EventBus -> WebSocket subscribed");
    }

    private JsonRpcMessage toNotification(Event event) {
        String method = switch (event.getType()) {
            case "run.started" -> "event.run.started";
            case "run.finished" -> "event.run.finished";
            case "step.started" -> "event.step.started";
            case "step.finished" -> "event.step.finished";
            case "tool.called" -> "event.tool.called";
            case "tool.result" -> "event.tool.result";
            case "llm.usage" -> "event.llm.usage";
            default -> "event." + event.getType().replace(".", ".");
        };
        return JsonRpcMessage.notification(method, event);
    }
}