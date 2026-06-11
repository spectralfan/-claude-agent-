package com.kama.jchatmind.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public class EventWriter implements Consumer<Object>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(EventWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path eventsFile;

    public EventWriter(Path eventsFile) {
        this.eventsFile = eventsFile;
        try {
            Files.createDirectories(eventsFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create events dir: " + eventsFile.getParent(), e);
        }
    }

    @Override
    public void accept(Object event) {
        try {
            ObjectNode row = MAPPER.createObjectNode();
            row.put("ts", java.time.Instant.now().toString());
            row.put("type", event.getClass().getSimpleName());
            row.set("data", MAPPER.valueToTree(event));
            String line = MAPPER.writeValueAsString(row) + "\n";
            Files.writeString(eventsFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write event to {}: {}", eventsFile, e.getMessage());
        }
    }

    @Override
    public void close() {
        // no-op for file-based writer
    }
}