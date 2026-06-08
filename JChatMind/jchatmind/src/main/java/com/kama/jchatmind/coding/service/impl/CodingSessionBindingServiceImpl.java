package com.kama.jchatmind.coding.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.coding.model.dto.CodingSessionBindingDTO;
import com.kama.jchatmind.coding.service.CodingSessionBindingService;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CodingSessionBindingServiceImpl implements CodingSessionBindingService {

    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<CodingSessionBindingDTO> findBinding(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !StringUtils.hasText(session.getMetadata())) {
            return Optional.empty();
        }
        try {
            Map<?, ?> root = objectMapper.readValue(session.getMetadata(), Map.class);
            Object coding = root.get("coding");
            if (coding == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.convertValue(coding, CodingSessionBindingDTO.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
