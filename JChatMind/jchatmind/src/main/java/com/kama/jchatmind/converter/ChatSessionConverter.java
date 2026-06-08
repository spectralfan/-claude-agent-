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

    public ChatSession toEntity(ChatSessionDTO chatSessionDTO) throws JsonProcessingException {
        Assert.notNull(chatSessionDTO, "ChatSessionDTO cannot be null");

        return ChatSession.builder()
                .id(chatSessionDTO.getId())
                .agentId(chatSessionDTO.getAgentId())
                .title(chatSessionDTO.getTitle())
                .metadata(chatSessionDTO.getMetadata() != null
                        ? writeSessionMetadata(chatSessionDTO.getMetadata())
                        : null)
                .createdAt(chatSessionDTO.getCreatedAt())
                .updatedAt(chatSessionDTO.getUpdatedAt())
                .build();
    }

    public ChatSessionDTO toDTO(ChatSession chatSession) throws JsonProcessingException {
        Assert.notNull(chatSession, "ChatSession cannot be null");

        return ChatSessionDTO.builder()
                .id(chatSession.getId())
                .agentId(chatSession.getAgentId())
                .title(chatSession.getTitle())
                .metadata(chatSession.getMetadata() != null 
                        ? objectMapper.readValue(chatSession.getMetadata(), ChatSessionDTO.MetaData.class) 
                        : null)
                .createdAt(chatSession.getCreatedAt())
                .updatedAt(chatSession.getUpdatedAt())
                .build();
    }

    public ChatSessionVO toVO(ChatSessionDTO dto) {
        return ChatSessionVO.builder()
                .id(dto.getId())
                .agentId(dto.getAgentId())
                .title(dto.getTitle())
                .build();
    }

    public ChatSessionVO toVO(ChatSession chatSession) throws JsonProcessingException {
        return toVO(toDTO(chatSession));
    }

    public ChatSessionDTO toDTO(CreateChatSessionRequest request) {
        Assert.notNull(request, "CreateChatSessionRequest cannot be null");
        Assert.notNull(request.getAgentId(), "AgentId cannot be null");

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
                .metadata(meta)
                .build();
    }

    private boolean hasCodingBinding(CreateChatSessionRequest request) {
        return request.getWorkspaceRoot() != null && !request.getWorkspaceRoot().isBlank();
    }

    private String writeSessionMetadata(ChatSessionDTO.MetaData meta) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode coding = objectMapper.createObjectNode();
        if (meta.getWorkspaceRoot() != null) {
            coding.put("workspaceRoot", meta.getWorkspaceRoot());
        }
        if (meta.getWorkspacePath() != null) {
            coding.put("workspacePath", meta.getWorkspacePath());
        }
        if (meta.getApprovalMode() != null) {
            coding.put("approvalMode", meta.getApprovalMode());
        }
        if (meta.getScaffoldOnCreate() != null) {
            coding.put("scaffoldOnCreate", meta.getScaffoldOnCreate());
        }
        root.set("coding", coding);
        return objectMapper.writeValueAsString(root);
    }

    public void updateDTOFromRequest(ChatSessionDTO dto, UpdateChatSessionRequest request) {
        Assert.notNull(dto, "ChatSessionDTO cannot be null");
        Assert.notNull(request, "UpdateChatSessionRequest cannot be null");

        if (request.getTitle() != null) {
            dto.setTitle(request.getTitle());
        }
    }
}
