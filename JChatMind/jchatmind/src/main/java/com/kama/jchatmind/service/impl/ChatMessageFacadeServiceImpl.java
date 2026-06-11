package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.agent.tools.ToolResultCompactor;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.event.ChatEvent;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ChatMessageMapper;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.model.dto.MemorySaveDTO;
import com.kama.jchatmind.memory.model.dto.ToolCallInfo;
import com.kama.jchatmind.memory.model.enums.MemoryRole;
import com.kama.jchatmind.memory.service.MemoryService;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.entity.ChatMessage;
import com.kama.jchatmind.model.request.CreateChatMessageRequest;
import com.kama.jchatmind.model.request.UpdateChatMessageRequest;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.response.GetChatMessagesResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.session.SessionManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ChatMessageFacadeServiceImpl implements ChatMessageFacadeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageConverter chatMessageConverter;
    private final ApplicationEventPublisher publisher;
    private final MemoryProperties memoryProperties;
    private final MemoryService memoryService;
    private final ToolResultCompactor toolResultCompactor;
    private final SessionManager sessionManager;

    @Override
    public GetChatMessagesResponse getChatMessagesBySessionId(String sessionId) {
        // 优先从 thread.jsonl 读取
        if (sessionManager.getThreadStore().exists(sessionId)) {
            List<ObjectNode> messages = sessionManager.getThreadStore().readMessages(sessionId);
            List<ChatMessageVO> result = new ArrayList<>();
            for (int i = 0; i < messages.size(); i++) {
                ObjectNode msg = messages.get(i);
                result.add(ChatMessageVO.builder()
                        .sessionId(sessionId)
                        .role(ChatMessageDTO.RoleType.fromRole(msg.get("role").asText()))
                        .content(msg.get("content").toString())
                        .build());
            }
            return GetChatMessagesResponse.builder()
                    .chatMessages(result.toArray(new ChatMessageVO[0]))
                    .build();
        }

        // 回退到 DB 读取
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionId(sessionId);
        List<ChatMessageVO> result = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            try {
                ChatMessageVO vo = chatMessageConverter.toVO(chatMessage);
                result.add(vo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetChatMessagesResponse.builder()
                .chatMessages(result.toArray(new ChatMessageVO[0]))
                .build();
    }

    @Override
    public List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit) {
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionIdRecently(sessionId, limit);
        List<ChatMessageDTO> result = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            try {
                ChatMessageDTO dto = chatMessageConverter.toDTO(chatMessage);
                result.add(dto);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Override
    public CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request) {
        ChatMessage chatMessage = doCreateChatMessage(request);
        publisher.publishEvent(new ChatEvent(
                request.getAgentId(),
                chatMessage.getSessionId(),
                chatMessage.getContent()
        ));
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    @Override
    public CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO) {
        ChatMessage chatMessage = doCreateChatMessage(chatMessageDTO);
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    private ChatMessage doCreateChatMessage(CreateChatMessageRequest request) {
        ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(request);
        return doCreateChatMessage(chatMessageDTO);
    }

    private ChatMessage doCreateChatMessage(ChatMessageDTO chatMessageDTO) {
        try {
            ChatMessage chatMessage = chatMessageConverter.toEntity(chatMessageDTO);
            LocalDateTime now = LocalDateTime.now();
            chatMessage.setCreatedAt(now);
            chatMessage.setUpdatedAt(now);
            int result = chatMessageMapper.insert(chatMessage);
            if (result <= 0) {
                throw new BizException("创建聊天消息失败");
            }

            // 同步写入 thread.jsonl
            try {
                String sessionId = chatMessage.getSessionId();
                if (sessionId != null) {
                    String role = chatMessageDTO.getRole() != null
                            ? chatMessageDTO.getRole().getRole()
                            : "user";
                    String content = chatMessage.getContent() != null
                            ? chatMessage.getContent()
                            : "";
                    sessionManager.getThreadStore().appendMessage(
                            sessionId, role, content, null);
                }
            } catch (Exception e) {
                log.warn("写入 thread.jsonl 失败 session={}: {}",
                        chatMessage.getSessionId(), e.getMessage());
            }

            mirrorToMemoryHub(chatMessageDTO, chatMessage.getSessionId());
            return chatMessage;
        } catch (JsonProcessingException e) {
            throw new BizException("创建聊天消息时发生序列化错误: " + e.getMessage());
        }
    }

    private void mirrorToMemoryHub(ChatMessageDTO dto, String sessionId) {
        if (!memoryProperties.isEnabled() || dto == null || dto.getRole() == null) {
            return;
        }
        try {
            List<ToolCallInfo> toolCalls = null;
            if (dto.getMetadata() != null && !CollectionUtils.isEmpty(dto.getMetadata().getToolCalls())) {
                toolCalls = dto.getMetadata().getToolCalls().stream()
                        .map(ToolCallInfo::from)
                        .toList();
            }
            String content = dto.getContent();
            if (ChatMessageDTO.RoleType.TOOL.equals(dto.getRole())
                    && dto.getMetadata() != null
                    && dto.getMetadata().getToolResponse() != null) {
                var toolResponse = dto.getMetadata().getToolResponse();
                content = toolResultCompactor.compact(toolResponse.name(), toolResponse.responseData());
            }
            MemorySaveDTO saveDTO = MemorySaveDTO.builder()
                    .sessionId(sessionId)
                    .role(MemoryRole.fromCode(dto.getRole().getRole()))
                    .content(content)
                    .toolCalls(toolCalls)
                    .build();
            memoryService.save(saveDTO);
        } catch (Exception e) {
            log.warn("镜像消息到 Memory Hub 失败 session={}, role={}: {}",
                    sessionId, dto.getRole(), e.getMessage());
        }
    }

    @Override
    public CreateChatMessageResponse appendChatMessage(String chatMessageId, String appendContent) {
        ChatMessage existingChatMessage = chatMessageMapper.selectById(chatMessageId);
        if (existingChatMessage == null) {
            throw new BizException("聊天消息不存在: " + chatMessageId);
        }
        String currentContent = existingChatMessage.getContent() != null
                ? existingChatMessage.getContent()
                : "";
        String updatedContent = currentContent + appendContent;
        ChatMessage updatedChatMessage = ChatMessage.builder()
                .id(existingChatMessage.getId())
                .sessionId(existingChatMessage.getSessionId())
                .role(existingChatMessage.getRole())
                .content(updatedContent)
                .metadata(existingChatMessage.getMetadata())
                .createdAt(existingChatMessage.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        int result = chatMessageMapper.updateById(updatedChatMessage);
        if (result <= 0) {
            throw new BizException("追加聊天消息内容失败");
        }
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessageId)
                .build();
    }

    @Override
    public void deleteChatMessage(String chatMessageId) {
        ChatMessage chatMessage = chatMessageMapper.selectById(chatMessageId);
        if (chatMessage == null) {
            throw new BizException("聊天消息不存在: " + chatMessageId);
        }
        int result = chatMessageMapper.deleteById(chatMessageId);
        if (result <= 0) {
            throw new BizException("删除聊天消息失败");
        }
    }

    @Override
    public void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request) {
        try {
            ChatMessage existingChatMessage = chatMessageMapper.selectById(chatMessageId);
            if (existingChatMessage == null) {
                throw new BizException("聊天消息不存在: " + chatMessageId);
            }
            ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(existingChatMessage);
            chatMessageConverter.updateDTOFromRequest(chatMessageDTO, request);
            ChatMessage updatedChatMessage = chatMessageConverter.toEntity(chatMessageDTO);
            updatedChatMessage.setId(existingChatMessage.getId());
            updatedChatMessage.setSessionId(existingChatMessage.getSessionId());
            updatedChatMessage.setRole(existingChatMessage.getRole());
            updatedChatMessage.setCreatedAt(existingChatMessage.getCreatedAt());
            updatedChatMessage.setUpdatedAt(LocalDateTime.now());
            int result = chatMessageMapper.updateById(updatedChatMessage);
            if (result <= 0) {
                throw new BizException("更新聊天消息失败");
            }
        } catch (JsonProcessingException e) {
            throw new BizException("更新聊天消息时发生序列化错误: " + e.getMessage());
        }
    }
}