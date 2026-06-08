package com.kama.jchatmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.service.impl.ChatSessionProvisionerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatSessionProvisionerTest {

    private ChatSessionMapper mapper;
    private ChatSessionProvisioner provisioner;

    @BeforeEach
    void setUp() {
        mapper = mock(ChatSessionMapper.class);
        provisioner = new ChatSessionProvisionerImpl(mapper, new ObjectMapper());
    }

    @Test
    void ensureSession_insertsWhenMissing() {
        String sessionId = UUID.randomUUID().toString();
        String agentId = UUID.randomUUID().toString();
        when(mapper.selectById(sessionId)).thenReturn(null);
        when(mapper.insertWithId(any(ChatSession.class))).thenReturn(1);

        provisioner.ensureSession(sessionId, agentId, "Worker: test", Map.of("hidden", true));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(mapper).insertWithId(captor.capture());
        assertEquals(sessionId, captor.getValue().getId());
        assertEquals(agentId, captor.getValue().getAgentId());
        assertNotNull(captor.getValue().getMetadata());
    }

    @Test
    void ensureSession_skipsWhenExists() {
        String sessionId = UUID.randomUUID().toString();
        when(mapper.selectById(sessionId)).thenReturn(ChatSession.builder().id(sessionId).build());

        provisioner.ensureSession(sessionId, UUID.randomUUID().toString(), "t", Map.of());

        verify(mapper, never()).insertWithId(any());
    }
}
