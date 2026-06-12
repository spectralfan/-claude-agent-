package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.ChatSessionConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.dto.ChatSessionDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.model.request.CreateChatSessionRequest;
import com.kama.jchatmind.model.request.UpdateChatSessionRequest;
import com.kama.jchatmind.model.response.CreateChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionsResponse;
import com.kama.jchatmind.model.vo.ChatSessionVO;
import com.kama.jchatmind.service.ChatSessionFacadeService;
import com.kama.jchatmind.session.SessionManager;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatSessionFacadeServiceImpl implements ChatSessionFacadeService {

    private static final String TOOL_SCHEDULER = "orchestration_task_tools";

    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionConverter chatSessionConverter;
    private final AgentMapper agentMapper;
    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    @Override
    public GetChatSessionsResponse getChatSessions() {
        return getChatSessionsByType(null);
    }

    @Override
    public GetChatSessionsResponse getChatSessionsByType(String type) {
        List<ChatSession> sessions;
        if (type != null && !type.isBlank()) {
            sessions = chatSessionMapper.selectByType(type);
        } else {
            sessions = chatSessionMapper.selectAll();
        }
        List<ChatSessionVO> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            if (isHiddenBackgroundSession(s)) continue;
            try { result.add(chatSessionConverter.toVO(s)); }
            catch (JsonProcessingException e) { throw new RuntimeException(e); }
        }
        return GetChatSessionsResponse.builder().chatSessions(result.toArray(new ChatSessionVO[0])).build();
    }

    @Override
    public GetChatSessionResponse getChatSession(String chatSessionId) {
        ChatSession entity = chatSessionMapper.selectById(chatSessionId);
        if (entity == null) throw new BizException("聊天会话不存在: " + chatSessionId);
        try {
            return GetChatSessionResponse.builder().chatSession(chatSessionConverter.toVO(entity)).build();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public GetChatSessionsResponse getChatSessionsByAgentId(String agentId) {
        List<ChatSession> sessions = chatSessionMapper.selectByAgentId(agentId);
        List<ChatSessionVO> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            if (isHiddenBackgroundSession(s)) continue;
            try { result.add(chatSessionConverter.toVO(s)); }
            catch (JsonProcessingException e) { throw new RuntimeException(e); }
        }
        return GetChatSessionsResponse.builder().chatSessions(result.toArray(new ChatSessionVO[0])).build();
    }

    @Override
    public CreateChatSessionResponse createChatSession(CreateChatSessionRequest request) {
        try {
            String type = resolveSessionType(request);
            String sid = sessionManager.createSession(
                    request.getAgentId(),
                    request.getTitle() != null ? request.getTitle() : "新会话",
                    type
            );
            return CreateChatSessionResponse.builder().chatSessionId(sid).build();} catch (Exception e) {
            throw new BizException("创建聊天会话失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteChatSession(String chatSessionId) {
        ChatSession entity = chatSessionMapper.selectById(chatSessionId);
        if (entity == null) throw new BizException("聊天会话不存在: " + chatSessionId);
        int r = chatSessionMapper.deleteById(chatSessionId);
        if (r <= 0) throw new BizException("删除聊天会话失败");
    }

    @Override
    public void updateChatSession(String chatSessionId, UpdateChatSessionRequest request) {
        try {
            ChatSession existing = chatSessionMapper.selectById(chatSessionId);
            if (existing == null) throw new BizException("聊天会话不存在: " + chatSessionId);
            ChatSessionDTO dto = chatSessionConverter.toDTO(existing);
            chatSessionConverter.updateDTOFromRequest(dto, request);
            ChatSession updated = chatSessionConverter.toEntity(dto);
            updated.setId(existing.getId());
            updated.setAgentId(existing.getAgentId());
            updated.setCreatedAt(existing.getCreatedAt());
            updated.setUpdatedAt(LocalDateTime.now());
            int r = chatSessionMapper.updateById(updated);
            if (r <= 0) throw new BizException("更新聊天会话失败");
        } catch (Exception e) {
            throw new BizException("更新聊天会话时发生序列化错误: " + e.getMessage());
        }
    }

    /**
     * 自动检测 session type：
     * 1. 请求已指定 type → 使用该 type
     * 2. Agent 包含 orchestration_task_tools → CODING
     * 3. 其他 → CHAT
     */
    private String resolveSessionType(CreateChatSessionRequest request) {
        if (request.getType() != null && !request.getType().isBlank()) {
            return request.getType();
        }
        if (request.getAgentId() != null) {
            Agent agent = agentMapper.selectById(request.getAgentId());
            if (agent != null && agent.getAllowedTools() != null) {
                try {
                    List<String> tools = objectMapper.readValue(agent.getAllowedTools(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                    if (tools.contains(TOOL_SCHEDULER)) {
                        return "CODING";
                    }
                } catch (Exception ignored) {}
            }
        }
        return "CHAT";
    }

    private boolean isHiddenBackgroundSession(ChatSession session) {
        if (session.getMetadata() == null || session.getMetadata().isBlank()) return false;
        try {
            var node = objectMapper.readTree(session.getMetadata());
            return node.path("hidden").asBoolean(false)
                    || "coding_subtask".equals(node.path("kind").asText(null));
        } catch (Exception e) { return false; }
    }
}