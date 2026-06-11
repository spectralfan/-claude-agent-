package com.kama.jchatmind.agent.tools.session;

import com.kama.jchatmind.session.store.NoteStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoteSaveToolTest {

    @TempDir
    Path tempDir;

    private NoteSaveTool tool;
    private NoteStore noteStore;

    @BeforeEach
    void setUp() {
        noteStore = new NoteStore(tempDir);
        tool = new NoteSaveTool(noteStore, "session-1", "run_001");
    }

    @Test
    void getName_shouldReturnSaveNote() {
        assertEquals("save_note", tool.getName());
    }

    @Test
    void getDescription_shouldNotBeEmpty() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }

    @Test
    void execute_shouldSaveNote() {
        String result = tool.execute("数据库使用 PostgreSQL，端口 5432");
        assertEquals("笔记已保存", result);
        String notes = noteStore.readNotes("session-1");
        assertTrue(notes.contains("PostgreSQL"));
        assertTrue(notes.contains("run_001"));
    }

    @Test
    void execute_emptyContent_shouldReturnError() {
        String result = tool.execute("");
        assertEquals("错误：内容不能为空", result);
    }

    @Test
    void execute_nullContent_shouldReturnError() {
        String result = tool.execute(null);
        assertEquals("错误：内容不能为空", result);
    }
}