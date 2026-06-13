package com.kama.jchatmind.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.model.request.CreateChatMessageRequest;
import com.kama.jchatmind.model.request.CreateChatSessionRequest;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.response.CreateChatSessionResponse;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.ChatSessionFacadeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JsonRpcDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcDispatcher.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatMessageFacadeService chatMessageService;
    private final ChatSessionFacadeService chatSessionService;

    public JsonRpcDispatcher(ChatMessageFacadeService cms, ChatSessionFacadeService css) {
        this.chatMessageService = cms; this.chatSessionService = css;
    }

    public JsonRpcMessage dispatch(JsonRpcMessage request) {
        if (!request.isRequest()) {
            return JsonRpcMessage.error(request.getId(), -32600, "Invalid Request");
        }
        try {
            switch (request.getMethod()) {
                case "chat.send": return handleChatSend(request);
                case "session.create": return handleSessionCreate(request);
                case "session.list": return handleSessionList(request);
                default: return JsonRpcMessage.error(request.getId(), -32601, "Method not found: " + request.getMethod());
            }
        } catch (Exception e) {
            log.warn("RPC error {}: {}", request.getMethod(), e.getMessage());
            return JsonRpcMessage.error(request.getId(), -32000, e.getMessage());
        }
    }

    private JsonRpcMessage handleChatSend(JsonRpcMessage req) throws Exception {
        ObjectNode p = mapper.valueToTree(req.getParams());
        CreateChatMessageRequest r = CreateChatMessageRequest.builder()
                .sessionId(p.has("sessionId") ? p.get("sessionId").asText() : null)
                .agentId(p.has("agentId") ? p.get("agentId").asText() : null)
                .content(p.has("content") ? p.get("content").asText() : "")
                .build();
        CreateChatMessageResponse resp = chatMessageService.createChatMessage(r);
        return JsonRpcMessage.response(req.getId(), mapper.createObjectNode().put("messageId", resp.getChatMessageId()));
    }

    private JsonRpcMessage handleSessionCreate(JsonRpcMessage req) throws Exception {
        ObjectNode p = mapper.valueToTree(req.getParams());
        CreateChatSessionRequest r = new CreateChatSessionRequest();
        r.setAgentId(p.has("agentId") ? p.get("agentId").asText() : null);
        r.setTitle(p.has("title") ? p.get("title").asText() : null);
        CreateChatSessionResponse resp = chatSessionService.createChatSession(r);
        return JsonRpcMessage.response(req.getId(), mapper.createObjectNode().put("sessionId", resp.getChatSessionId()));
    }

    private JsonRpcMessage handleSessionList(JsonRpcMessage req) throws Exception {
        ObjectNode p = mapper.valueToTree(req.getParams());
        String type = p.has("type") ? p.get("type").asText() : null;
        var sessions = (type != null && !type.isBlank())
                ? chatSessionService.getChatSessionsByType(type)
                : chatSessionService.getChatSessions();
        return JsonRpcMessage.response(req.getId(), sessions);
    }
}