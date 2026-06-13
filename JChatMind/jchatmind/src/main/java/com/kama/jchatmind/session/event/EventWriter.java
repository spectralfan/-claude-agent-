package com.kama.jchatmind.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public class EventWriter implements Consumer<Event>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(EventWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path eventsFile;

    public EventWriter(Path eventsFile) {
        this.eventsFile = eventsFile;
        try { Files.createDirectories(eventsFile.getParent()); }
        catch (IOException e) { throw new RuntimeException("Cannot create events dir", e); }
    }

    @Override
    public void accept(Event event) {
        try {
            String line = MAPPER.writeValueAsString(event) + "\n";
            Files.writeString(eventsFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("EventWriter failed for {}: {}", event.getType(), e.getMessage());
        }
    }

    @Override
    public void close() {}
}