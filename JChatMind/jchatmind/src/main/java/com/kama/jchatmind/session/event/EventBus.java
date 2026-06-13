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
    private final List<Consumer<? super Event>> handlers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<? super Event> handler) {
        if (handler != null) handlers.add(handler);
    }

    public void unsubscribe(Consumer<? super Event> handler) {
        handlers.remove(handler);
    }

    public void publish(Event event) {
        for (Consumer<? super Event> handler : handlers) {
            try { handler.accept(event); }
            catch (Exception e) { log.warn("EventBus error for {}: {}", event.getType(), e.getMessage()); }
        }
    }
}