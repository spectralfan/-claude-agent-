package com.kama.jchatmind.session.store;

import com.kama.jchatmind.session.SessionMeta;
import com.kama.jchatmind.session.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MetaStoreTest {

    @TempDir
    Path tempDir;

    private MetaStore store;

    @BeforeEach
    void setUp() {
        store = new MetaStore(tempDir);
    }

    @Test
    void writeAndRead_shouldRoundtrip() {
        SessionMeta meta = new SessionMeta("session-1", "agent-1", "测试会话");
        meta.setState(SessionState.ACTIVE);
        meta.setRunCount(3);
        meta.setLastRunId("run_abc123");
        store.writeMeta("session-1", meta);

        SessionMeta loaded = store.readMeta("session-1");
        assertNotNull(loaded);
        assertEquals("session-1", loaded.getSessionId());
        assertEquals("agent-1", loaded.getAgentId());
        assertEquals(SessionState.ACTIVE, loaded.getState());
        assertEquals(3, loaded.getRunCount());
        assertEquals("run_abc123", loaded.getLastRunId());
    }

    @Test
    void readMeta_nonexistent_shouldReturnNull() {
        assertNull(store.readMeta("nonexistent"));
    }

    @Test
    void deleteMeta_shouldRemoveFile() {
        SessionMeta meta = new SessionMeta("session-1", "agent-1", "test");
        store.writeMeta("session-1", meta);
        assertNotNull(store.readMeta("session-1"));
        store.deleteMeta("session-1");
        assertNull(store.readMeta("session-1"));
    }
}