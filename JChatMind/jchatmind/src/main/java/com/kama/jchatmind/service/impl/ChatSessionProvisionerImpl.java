package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.service.ChatSessionProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionProvisionerImpl implements ChatSessionProvisioner {

    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void ensureSession(String sessionId, String agentId, String title, Map<String, Object> metadata) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("sessionId 与 agentId 不能为空");
        }
        if (chatSessionMapper.selectById(sessionId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = ChatSession.builder()
                .id(sessionId)
                .agentId(agentId)
                .title(StringUtils.hasText(title) ? title : "后台会话")
                .type("CODING").metadata(toMetadataJson(metadata))
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            int rows = chatSessionMapper.insertWithId(session);
            if (rows <= 0) {
                throw new IllegalStateException("注册 chat_session 失败: " + sessionId);
            }
            log.info("已注册 chat_session: id={} agentId={} title={}", sessionId, agentId, title);
        } catch (DuplicateKeyException e) {
            log.debug("chat_session 已存在（并发注册）: {}", sessionId);
        }
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 session metadata 失败", e);
        }
    }
}
