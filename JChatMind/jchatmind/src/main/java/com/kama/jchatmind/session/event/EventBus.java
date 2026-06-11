package com.kama.jchatmind.session.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);
    private final List<Consumer<Object>> handlers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<Object> handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    public void unsubscribe(Consumer<Object> handler) {
        handlers.remove(handler);
    }

    public void publish(Object event) {
        for (Consumer<Object> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.warn("EventBus handler error for event={}: {}",
                        event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}