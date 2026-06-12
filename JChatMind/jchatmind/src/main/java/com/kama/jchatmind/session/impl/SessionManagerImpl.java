package com.kama.jchatmind.session.impl;

import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.session.*;
import com.kama.jchatmind.session.config.SessionProperties;
import com.kama.jchatmind.session.store.MetaStore;
import com.kama.jchatmind.session.store.NoteStore;
import com.kama.jchatmind.session.store.ThreadStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessionManagerImpl implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);

    private final ThreadStore threadStore;
    private final NoteStore noteStore;
    private final MetaStore metaStore;
    private final ChatSessionMapper chatSessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Path sessionRoot;

    public SessionManagerImpl(SessionProperties sp, ChatSessionMapper mapper, ApplicationEventPublisher ep) {
        this.sessionRoot = Path.of(sp.getStoreRoot()).toAbsolutePath().normalize();
        this.threadStore = new ThreadStore(this.sessionRoot);
        this.noteStore = new NoteStore(this.sessionRoot);
        this.metaStore = new MetaStore(this.sessionRoot);
        this.chatSessionMapper = mapper;
        this.eventPublisher = ep;
    }

    @Override
    public String createSession(String agentId, String title) {
        return createSession(agentId, title, "CHAT");
    }

    @Override
    public String createSession(String agentId, String title, String type) {
        String sessionId = UUID.randomUUID().toString();
        String t = (type != null && !type.isBlank()) ? type : "CHAT";
        String title2 = (title != null && !title.isBlank()) ? title : "新会话";
        ChatSession entity = ChatSession.builder()
                .id(sessionId)
                .agentId(agentId)
                .title(title2)
                .type(t)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(entity);
        SessionMeta meta = new SessionMeta(sessionId, agentId, title2);
        meta.setState(SessionState.CREATED);
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.Created(this, sessionId));
        log.info("Session created: id={} agentId={} type={}", sessionId, agentId, t);
        return sessionId;
    }

    public void activateSession(String sid) { transitionState(sid, SessionState.ACTIVE); }
    public void pauseSession(String sid) { transitionState(sid, SessionState.PAUSED); }
    public void completeSession(String sid) { transitionState(sid, SessionState.COMPLETED); }

    public void failSession(String sessionId, String reason) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) { log.warn("Session not found: {}", sessionId); return; }
        SessionState old = meta.getState();
        meta.setState(SessionState.FAILED);
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.StateChanged(this, sessionId, old, SessionState.FAILED));
    }

    public String startRun(String sessionId, String goal) {
        String runId = SessionRunIdGenerator.newRunId();
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) { return runId; }
        meta.setState(SessionState.ACTIVE);
        meta.setRunCount(meta.getRunCount() + 1);
        meta.setLastRunId(runId);
        meta.setLastActiveAt(LocalDateTime.now());
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.RunStarted(this, sessionId, runId, goal));
        return runId;
    }

    public void finishRun(String sessionId, String runId, String status, String reason) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) return;
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.RunFinished(this, sessionId, runId, status, reason));
    }

    public ThreadStore getThreadStore() { return threadStore; }
    public NoteStore getNoteStore() { return noteStore; }
    public MetaStore getMetaStore() { return metaStore; }
    public SessionMeta getSession(String sid) { return metaStore.readMeta(sid); }

    public boolean isActive(String sid) {
        SessionMeta meta = metaStore.readMeta(sid);
        return meta != null && meta.getState() == SessionState.ACTIVE;
    }

    private void transitionState(String sessionId, SessionState target) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) return;
        SessionState old = meta.getState();
        if (old == SessionState.COMPLETED || old == SessionState.FAILED) return;
        meta.setState(target);
        meta.setUpdatedAt(LocalDateTime.now());
        if (target == SessionState.ACTIVE) meta.setLastActiveAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.StateChanged(this, sessionId, old, target));
    }
}