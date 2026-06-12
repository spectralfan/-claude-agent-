package com.kama.jchatmind.session;
import com.kama.jchatmind.session.store.NoteStore;
import com.kama.jchatmind.session.store.ThreadStore;
import com.kama.jchatmind.session.store.MetaStore;

public interface SessionManager {
    String createSession(String agentId, String title);
    String createSession(String agentId, String title, String type);
    void activateSession(String sessionId);
    void pauseSession(String sessionId);
    void completeSession(String sessionId);
    void failSession(String sessionId, String reason);
    String startRun(String sessionId, String goal);
    void finishRun(String sessionId, String runId, String status, String reason);
    ThreadStore getThreadStore();
    NoteStore getNoteStore();
    MetaStore getMetaStore();
    SessionMeta getSession(String sessionId);
    boolean isActive(String sessionId);
}