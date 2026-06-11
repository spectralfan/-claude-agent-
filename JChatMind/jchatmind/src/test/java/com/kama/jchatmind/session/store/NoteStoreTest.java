package com.kama.jchatmind.session.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class NoteStoreTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-12T10:00:00Z"), ZoneId.of("UTC"));

    @TempDir
    Path tempDir;

    private NoteStore store;

    @BeforeEach
    void setUp() {
        store = new NoteStore(tempDir, FIXED_CLOCK);
    }

    @Test
    void appendNote_shouldCreateFile() {
        store.appendNote("session-1", "用户要求使用 JWT 认证", "run_001");
        String notes = store.readNotes("session-1");
        assertTrue(notes.contains("JWT"));
        assertTrue(notes.contains("run_001"));
        assertTrue(notes.contains("## Note"));
        assertTrue(notes.contains("2026-06-12T10:00:00Z"));
    }

    @Test
    void appendNote_multiple_shouldAppend() {
        store.appendNote("session-1", "第一条事实", "run_001");
        store.appendNote("session-1", "第二条事实", "run_002");
        String notes = store.readNotes("session-1");
        assertTrue(notes.contains("第一条事实"));
        assertTrue(notes.contains("第二条事实"));
        assertTrue(notes.contains("run_001"));
        assertTrue(notes.contains("run_002"));
    }

    @Test
    void readNotes_emptyFile_shouldReturnEmpty() {
        assertEquals("", store.readNotes("nonexistent"));
    }

    @Test
    void deleteNotes_shouldRemoveFile() {
        store.appendNote("session-1", "temp", "run_001");
        assertTrue(store.readNotes("session-1").contains("temp"));
        store.deleteNotes("session-1");
        assertEquals("", store.readNotes("session-1"));
    }
}