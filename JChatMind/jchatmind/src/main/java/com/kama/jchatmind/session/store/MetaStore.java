package com.kama.jchatmind.session.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kama.jchatmind.session.SessionMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * meta.json 读写 — Session 元数据持久化。
 */
public class MetaStore {

    private static final Logger log = LoggerFactory.getLogger(MetaStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Path sessionRoot;

    public MetaStore(Path sessionRoot) {
        this.sessionRoot = sessionRoot;
    }

    public void writeMeta(String sessionId, SessionMeta meta) {
        Path file = resolveMetaFile(sessionId);
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), meta);
        } catch (IOException e) {
            log.error("Failed to write meta.json for session={}", sessionId, e);
        }
    }

    public SessionMeta readMeta(String sessionId) {
        Path file = resolveMetaFile(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return MAPPER.readValue(file.toFile(), SessionMeta.class);
        } catch (IOException e) {
            log.warn("Failed to read meta.json for session={}", sessionId, e);
            return null;
        }
    }

    public void deleteMeta(String sessionId) {
        Path file = resolveMetaFile(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete meta.json for session={}", sessionId, e);
        }
    }

    Path resolveMetaFile(String sessionId) {
        return sessionRoot.resolve(sessionId).resolve("meta.json");
    }
}