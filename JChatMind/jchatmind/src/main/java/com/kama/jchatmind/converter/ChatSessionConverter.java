package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.model.dto.ChatSessionDTO;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.model.request.CreateChatSessionRequest;
import com.kama.jchatmind.model.request.UpdateChatSessionRequest;
import com.kama.jchatmind.model.vo.ChatSessionVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@AllArgsConstructor
public class ChatSessionConverter {

    private final ObjectMapper objectMapper;

    public ChatSession toEntity(ChatSessionDTO dto) throws JsonProcessingException {
        Assert.notNull(dto, "ChatSessionDTO cannot be null");
        return ChatSession.builder()
                .id(dto.getId())
                .agentId(dto.getAgentId())
                .title(dto.getTitle())
                .type(dto.getType())
                .metadata(dto.getMetadata() != null ? writeSessionMetadata(dto.getMetadata()) : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public ChatSessionDTO toDTO(ChatSession entity) throws JsonProcessingException {
        Assert.notNull(entity, "ChatSession cannot be null");
        ChatSessionDTO.MetaData meta = null;
        if (entity.getMetadata() != null) {
            meta = objectMapper.readValue(entity.getMetadata(), ChatSessionDTO.MetaData.class);
        }
        return ChatSessionDTO.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .title(entity.getTitle())
                .type(entity.getType())
                .metadata(meta)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ChatSessionVO toVO(ChatSession entity) throws JsonProcessingException {
        ChatSessionDTO dto = toDTO(entity);
        return ChatSessionVO.builder()
                .id(dto.getId())
                .agentId(dto.getAgentId())
                .title(dto.getTitle())
                .type(dto.getType())
                .build();
    }

    public ChatSessionDTO toDTO(CreateChatSessionRequest request) {
        Assert.notNull(request, "CreateChatSessionRequest cannot be null");
        ChatSessionDTO.MetaData meta = null;
        if (hasCodingBinding(request)) {
            meta = new ChatSessionDTO.MetaData();
            meta.setWorkspaceRoot(request.getWorkspaceRoot());
            meta.setWorkspacePath(request.getWorkspacePath());
            meta.setApprovalMode(request.getApprovalMode());
            meta.setScaffoldOnCreate(request.getScaffoldOnCreate());
        }
        return ChatSessionDTO.builder()
                .agentId(request.getAgentId())
                .title(request.getTitle())
                .type(request.getType())
                .metadata(meta)
                .build();
    }

    private boolean hasCodingBinding(CreateChatSessionRequest request) {
        return request.getWorkspaceRoot() != null && !request.getWorkspaceRoot().isBlank();
    }

    private String writeSessionMetadata(ChatSessionDTO.MetaData meta) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode coding = objectMapper.createObjectNode();
        if (meta.getWorkspaceRoot() != null) coding.put("workspaceRoot", meta.getWorkspaceRoot());
        if (meta.getWorkspacePath() != null) coding.put("workspacePath", meta.getWorkspacePath());
        if (meta.getApprovalMode() != null) coding.put("approvalMode", meta.getApprovalMode());
        if (meta.getScaffoldOnCreate() != null) coding.put("scaffoldOnCreate", meta.getScaffoldOnCreate());
        root.set("coding", coding);
        return objectMapper.writeValueAsString(root);
    }

    public void updateDTOFromRequest(ChatSessionDTO dto, UpdateChatSessionRequest request) {
        if (request.getTitle() != null) dto.setTitle(request.getTitle());
    }
}