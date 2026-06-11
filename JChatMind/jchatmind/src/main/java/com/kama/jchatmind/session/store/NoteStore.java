package com.kama.jchatmind.session.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;

/**
 * Notes.md 文件存储 — Agent 主动创造的事实记忆。
 *
 * <p>文件位置：{sessionRoot}/{sessionId}/notes.md</p>
 *
 * <p>格式：</p>
 * <pre>
 * ## Note (2026-06-12T10:00:00Z, run_001)
 * 用户要求使用 JWT 进行身份验证，token 有效期 24 小时。
 * </pre>
 */
public class NoteStore {

    private static final Logger log = LoggerFactory.getLogger(NoteStore.class);

    private final Path sessionRoot;
    private final Clock clock;

    public NoteStore(Path sessionRoot) {
        this(sessionRoot, Clock.systemUTC());
    }

    NoteStore(Path sessionRoot, Clock clock) {
        this.sessionRoot = sessionRoot;
        this.clock = clock;
    }

    /**
     * 读取 notes.md 全文，文件不存在时返回空字符串。
     */
    public String readNotes(String sessionId) {
        Path file = resolveNotesFile(sessionId);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read notes.md for session={}", sessionId, e);
            return "";
        }
    }

    /**
     * 追加一条 Agent 主动事实到 notes.md。
     */
    public void appendNote(String sessionId, String content, String runId) {
        Path file = resolveNotesFile(sessionId);
        try {
            Files.createDirectories(file.getParent());
            String entry = String.format("## Note (%s, %s)\n%s\n\n",
                    Instant.now(clock).toString(), runId, content);
            Files.writeString(file, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Note appended to session={} run={}", sessionId, runId);
        } catch (IOException e) {
            log.error("Failed to append note to session={}", sessionId, e);
        }
    }

    /**
     * 删除 notes.md 文件。
     */
    public void deleteNotes(String sessionId) {
        Path file = resolveNotesFile(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete notes.md for session={}", sessionId, e);
        }
    }

    Path resolveNotesFile(String sessionId) {
        return sessionRoot.resolve(sessionId).resolve("notes.md");
    }
}