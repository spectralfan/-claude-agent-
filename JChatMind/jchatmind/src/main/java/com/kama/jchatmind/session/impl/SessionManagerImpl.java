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

    public SessionManagerImpl(
            SessionProperties sessionProperties,
            ChatSessionMapper chatSessionMapper,
            ApplicationEventPublisher eventPublisher) {
        this.sessionRoot = Path.of(sessionProperties.getStoreRoot()).toAbsolutePath().normalize();
        this.threadStore = new ThreadStore(this.sessionRoot);
        this.noteStore = new NoteStore(this.sessionRoot);
        this.metaStore = new MetaStore(this.sessionRoot);
        this.chatSessionMapper = chatSessionMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String createSession(String agentId, String title) {
        String sessionId = UUID.randomUUID().toString();

        ChatSession entity = ChatSession.builder()
                .id(sessionId)
                .agentId(agentId)
                .title(title != null ? title : "新会话")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(entity);

        SessionMeta meta = new SessionMeta(sessionId, agentId, title);
        meta.setState(SessionState.CREATED);
        metaStore.writeMeta(sessionId, meta);

        eventPublisher.publishEvent(new SessionEvent.Created(this, sessionId));
        log.info("Session created: id={} agentId={}", sessionId, agentId);
        return sessionId;
    }

    @Override
    public void activateSession(String sessionId) {
        transitionState(sessionId, SessionState.ACTIVE);
    }

    @Override
    public void pauseSession(String sessionId) {
        transitionState(sessionId, SessionState.PAUSED);
    }

    @Override
    public void completeSession(String sessionId) {
        transitionState(sessionId, SessionState.COMPLETED);
    }

    @Override
    public void failSession(String sessionId, String reason) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }
        SessionState oldState = meta.getState();
        meta.setState(SessionState.FAILED);
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.StateChanged(this, sessionId, oldState, SessionState.FAILED));
        log.info("Session failed: id={} reason={}", sessionId, reason);
    }

    @Override
    public String startRun(String sessionId, String goal) {
        String runId = SessionRunIdGenerator.newRunId();

        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) {
            log.warn("Session not found: {}", sessionId);
            return runId;
        }
        meta.setState(SessionState.ACTIVE);
        meta.setRunCount(meta.getRunCount() + 1);
        meta.setLastRunId(runId);
        meta.setLastActiveAt(LocalDateTime.now());
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);

        eventPublisher.publishEvent(new SessionEvent.RunStarted(this, sessionId, runId, goal));
        log.info("Run started: session={} runId={} goal={}", sessionId, runId, goal);
        return runId;
    }

    @Override
    public void finishRun(String sessionId, String runId, String status, String reason) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }
        meta.setUpdatedAt(LocalDateTime.now());
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.RunFinished(this, sessionId, runId, status, reason));
        log.info("Run finished: session={} runId={} status={}", sessionId, runId, status);
    }

    @Override
    public ThreadStore getThreadStore() { return threadStore; }
    @Override
    public NoteStore getNoteStore() { return noteStore; }
    @Override
    public MetaStore getMetaStore() { return metaStore; }

    @Override
    public SessionMeta getSession(String sessionId) { return metaStore.readMeta(sessionId); }

    @Override
    public boolean isActive(String sessionId) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        return meta != null && meta.getState() == SessionState.ACTIVE;
    }

    private void transitionState(String sessionId, SessionState target) {
        SessionMeta meta = metaStore.readMeta(sessionId);
        if (meta == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }
        SessionState oldState = meta.getState();
        if (oldState == SessionState.COMPLETED || oldState == SessionState.FAILED) {
            log.warn("Cannot transition session {} from {} to {}", sessionId, oldState, target);
            return;
        }
        meta.setState(target);
        meta.setUpdatedAt(LocalDateTime.now());
        if (target == SessionState.ACTIVE) {
            meta.setLastActiveAt(LocalDateTime.now());
        }
        metaStore.writeMeta(sessionId, meta);
        eventPublisher.publishEvent(new SessionEvent.StateChanged(this, sessionId, oldState, target));
        log.info("Session state changed: id={} {} -> {}", sessionId, oldState, target);
    }
}