package com.kama.jchatmind.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.agent.config.AgentToolResultProperties;
import com.kama.jchatmind.agent.memory.ToolAwareMessageWindowChatMemory;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.profile.AgentProfile;
import com.kama.jchatmind.agent.tools.ToolRegistry;
import com.kama.jchatmind.agent.tools.ToolResultCompactor;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.coding.CodingAgentRoles;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingPromptComposer;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.mcp.bridge.McpToolBridge;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.integration.McpIntegration;
import com.kama.jchatmind.mcp.integration.McpIntegrationImpl;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.integration.MemoryIntegration;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import com.kama.jchatmind.service.ToolFacadeService;
import com.kama.jchatmind.session.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@org.springframework.cache.annotation.EnableCaching
public class JChatMindFactory {

    private static final Logger log = LoggerFactory.getLogger(JChatMindFactory.class);
    private final ChatClientRegistry chatClientRegistry;
    private final ChatEventPublisher chatEventPublisher;
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final ToolFacadeService toolFacadeService;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final ChatMessageConverter chatMessageConverter;
    private final MemoryProperties memoryProperties;
    private final MemoryIntegration memoryIntegration;
    private final McpProperties mcpProperties;
    private final McpIntegration mcpIntegration;
    private final McpToolBridge mcpToolBridge;
    private final CodingPromptComposer codingPromptComposer;
    private final CodingTaskService codingTaskService;
    private final CodingProperties codingProperties;
    private final CodingSubagentProperties codingSubagentProperties;
    private final ToolResultCompactor toolResultCompactor;
    private final AgentToolResultProperties agentToolResultProperties;
    private final ChatSessionMapper chatSessionMapper;
    private final EventBus eventBus;
    private final ToolRegistry toolRegistry;

    // 运行时 Agent 配置
    private AgentDTO agentConfig;

    public JChatMindFactory(
            ChatClientRegistry chatClientRegistry,
            ChatEventPublisher chatEventPublisher,
            AgentMapper agentMapper,
            AgentConverter agentConverter,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeBaseConverter knowledgeBaseConverter,
            ToolFacadeService toolFacadeService,
            ChatMessageFacadeService chatMessageFacadeService,
            ChatMessageConverter chatMessageConverter,
            MemoryProperties memoryProperties,
            MemoryIntegration memoryIntegration,
            McpProperties mcpProperties,
            McpIntegration mcpIntegration,
            McpToolBridge mcpToolBridge,
            CodingPromptComposer codingPromptComposer,
            CodingTaskService codingTaskService,
            CodingProperties codingProperties,
            CodingSubagentProperties codingSubagentProperties,
            ToolResultCompactor toolResultCompactor,
            AgentToolResultProperties agentToolResultProperties,
            ChatSessionMapper chatSessionMapper,
            EventBus eventBus,
            ToolRegistry toolRegistry
    ) {
        this.chatClientRegistry = chatClientRegistry;
        this.chatEventPublisher = chatEventPublisher;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseConverter = knowledgeBaseConverter;
        this.toolFacadeService = toolFacadeService;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.memoryProperties = memoryProperties;
        this.memoryIntegration = memoryIntegration;
        this.mcpProperties = mcpProperties;
        this.mcpIntegration = mcpIntegration;
        this.mcpToolBridge = mcpToolBridge;
        this.codingPromptComposer = codingPromptComposer;
        this.codingTaskService = codingTaskService;
        this.codingProperties = codingProperties;
        this.codingSubagentProperties = codingSubagentProperties;
        this.toolResultCompactor = toolResultCompactor;
        this.agentToolResultProperties = agentToolResultProperties;
        this.eventBus = eventBus;
        this.chatSessionMapper = chatSessionMapper;
        this.toolRegistry = toolRegistry;
    }

    private String buildSystemPromptWithRules(String basePrompt, String chatSessionId) {
        return codingPromptComposer.composeSystemPrompt(basePrompt, chatSessionId, agentConfig);
    }

    @org.springframework.cache.annotation.Cacheable(value = "agents", key = "#agentId")
    private Agent loadAgent(String agentId) {
        return agentMapper.selectById(agentId);
    }

    /**
     * 将数据库中存储的记忆恢复成 List<Message> 结构
     */
    private List<Message> loadMemory(String chatSessionId) {
        return loadMemory(chatSessionId, null);
    }

    private List<Message> loadMemory(String chatSessionId, String enrichedUserInput) {
        List<Message> memory = loadChatMessageHistory(chatSessionId, enrichedUserInput);

        // Memory Hub：在 chat_message 主链路之外注入 RECENT/ARCHIVE 历史摘要（保留 tool 链完整性）
        CodingTask activeCodingTask = codingTaskService.getActiveTask(chatSessionId);
        boolean injectSupplemental = false;
        String supplementalLabel = "以下是与当前会话相关的历史记忆（来自 Memory Hub）：";
        if (memoryProperties.isEnabled()) {
            if (activeCodingTask != null) {
                injectSupplemental = memoryProperties.isCodingSupplementalEnabled();
                supplementalLabel = "以下是与当前 Coding 任务相关的历史记忆（RECENT/ARCHIVE，来自 Memory Hub）：";
            } else {
                injectSupplemental = memoryProperties.isSupplementalEnabled();
            }
        }
        if (injectSupplemental) {
            try {
                List<Message> supplemental = memoryIntegration.buildSupplementalContext(chatSessionId, 0);
                if (supplemental != null && !supplemental.isEmpty()) {
                    List<Message> merged = new ArrayList<>();
                    merged.add(new SystemMessage(supplementalLabel));
                    merged.addAll(supplemental);
                    merged.addAll(memory);
                    memory = merged;
                    log.debug("Memory Hub 已为 session={} 注入 {} 条补充记忆 (coding={})",
                            chatSessionId, supplemental.size(), activeCodingTask != null);
                }
            } catch (Exception e) {
                log.warn("Memory Hub 补充记忆加载失败 session={}: {}", chatSessionId, e.getMessage());
            }
        }
        return memory;
    }

    private List<Message> loadChatMessageHistory(String chatSessionId, String enrichedUserInput) {
        int messageLength = resolveMemoryWindow(chatSessionId, agentConfig, false);
        List<ChatMessageDTO> chatMessages = chatMessageFacadeService.getChatMessagesBySessionIdRecently(chatSessionId, messageLength);
        List<Message> memory = new ArrayList<>();
        for (ChatMessageDTO chatMessageDTO : chatMessages) {
            switch (chatMessageDTO.getRole()) {
                case SYSTEM:
                    if (!StringUtils.hasLength(chatMessageDTO.getContent())) continue;
                    memory.add(0, new SystemMessage(chatMessageDTO.getContent()));
                    break;
                case USER:
                    if (!StringUtils.hasLength(chatMessageDTO.getContent())) continue;
                    memory.add(new UserMessage(chatMessageDTO.getContent()));
                    break;
                case ASSISTANT:
                    memory.add(AssistantMessage.builder()
                            .content(chatMessageDTO.getContent())
                            .toolCalls(chatMessageDTO.getMetadata()
                                    .getToolCalls())
                            .build());
                    break;
                case TOOL:
                    memory.add(ToolResponseMessage.builder()
                            .responses(List.of(chatMessageDTO
                                    .getMetadata()
                                    .getToolResponse()))
                            .build());
                    break;
                default:
                    log.error("不支持的 Message 类型: {}, content = {}",
                            chatMessageDTO.getRole().getRole(),
                            chatMessageDTO.getContent()
                    );
                    throw new IllegalStateException("不支持的 Message 类型");
            }
        }
        if (StringUtils.hasLength(enrichedUserInput)) {
            for (int i = memory.size() - 1; i >= 0; i--) {
                if (memory.get(i) instanceof UserMessage) {
                    memory.set(i, new UserMessage(enrichedUserInput));
                    break;
                }
            }
        }
        compactHistoricalToolResults(memory);
        return memory;
    }

    /**
     * 对较早的 tool round 压缩 responseData，最近 N 轮保持完整供多步推理。
     */
    private void compactHistoricalToolResults(List<Message> memory) {
        if (!agentToolResultProperties.isEnabled() || memory.isEmpty()) {
            return;
        }
        Set<Integer> pinned = resolvePinnedMessageIndices(memory);
        List<List<Integer>> rounds = ToolAwareMessageWindowChatMemory.partitionRoundIndices(memory, pinned);
        List<List<Integer>> toolRounds = new ArrayList<>();
        for (List<Integer> round : rounds) {
            boolean hasTool = round.stream().anyMatch(idx -> memory.get(idx) instanceof ToolResponseMessage);
            if (hasTool) {
                toolRounds.add(round);
            }
        }
        int preserve = Math.max(0, agentToolResultProperties.getPreserveRecentRounds());
        int compactThrough = toolRounds.size() - preserve;
        if (compactThrough <= 0) {
            return;
        }
        Set<Integer> compactIndices = new HashSet<>();
        for (int r = 0; r < compactThrough; r++) {
            compactIndices.addAll(toolRounds.get(r));
        }
        for (int i = 0; i < memory.size(); i++) {
            if (!compactIndices.contains(i)) {
                continue;
            }
            Message message = memory.get(i);
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            List<ToolResponseMessage.ToolResponse> compacted = toolResponseMessage.getResponses().stream()
                    .map(resp -> new ToolResponseMessage.ToolResponse(
                            resp.id(),
                            resp.name(),
                            toolResultCompactor.compact(resp.name(), resp.responseData())))
                    .toList();
            memory.set(i, ToolResponseMessage.builder().responses(compacted).build());
        }
    }

    private Set<Integer> resolvePinnedMessageIndices(List<Message> messages) {
        Set<Integer> pinned = new HashSet<>();
        boolean firstUserPinned = false;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof SystemMessage) {
                pinned.add(i);
            } else if (!firstUserPinned && message instanceof UserMessage) {
                pinned.add(i);
                firstUserPinned = true;
            }
        }
        return pinned;
    }

    private AgentDTO toAgentConfig(Agent agent) {
        try {
            agentConfig = agentConverter.toDTO(agent);
            return agentConfig;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析 Agent 配置失败", e);
        }
    }

    private List<KnowledgeBaseDTO> resolveRuntimeKnowledgeBases(AgentDTO agentConfig) {
        return resolveRuntimeKnowledgeBases(agentConfig, null);
    }

    private List<KnowledgeBaseDTO> resolveRuntimeKnowledgeBases(AgentDTO agentConfig, String sessionId) {
        if (sessionId != null && codingTaskService.getActiveTask(sessionId) != null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> allowedKbIds = new LinkedHashSet<>();
        if (agentConfig.getAllowedKbs() != null) {
            agentConfig.getAllowedKbs().stream()
                    .filter(StringUtils::hasText)
                    .forEach(allowedKbIds::add);
        }
        if (allowedKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectByIdBatch(new ArrayList<>(allowedKbIds));
        if (knowledgeBases.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeBaseDTO> kbDTOs = new ArrayList<>();
        try {
            for (KnowledgeBase knowledgeBase : knowledgeBases) {
                KnowledgeBaseDTO kbDTO = knowledgeBaseConverter.toDTO(knowledgeBase);
                kbDTOs.add(kbDTO);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return kbDTOs;
    }

    private JChatMind buildAgentRuntime(
            Agent agent,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId
    ) {
        return buildAgentRuntime(agent, memory, knowledgeBases, toolCallbacks, chatSessionId, chatSessionId);
    }

    private JChatMind buildAgentRuntime(
            Agent agent,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            String taskSessionId
    ) {
        return buildAgentRuntime(agent, memory, knowledgeBases, toolCallbacks, chatSessionId, taskSessionId, false);
    }

    private JChatMind buildAgentRuntime(
            Agent agent,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            String taskSessionId,
            boolean subAgent
    ) {
        return buildJChatMindInstance(
                agent,
                buildSystemPromptWithRules(agent.getSystemPrompt(), taskSessionId),
                memory,
                knowledgeBases,
                toolCallbacks,
                chatSessionId,
                taskSessionId,
                subAgent
        );
    }

    private JChatMind buildAgentRuntimeWithCustomSystem(
            Agent agent,
            String systemPrompt,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            String taskSessionId,
            boolean subAgent
    ) {
        return buildJChatMindInstance(
                agent, systemPrompt, memory, knowledgeBases, toolCallbacks,
                chatSessionId, taskSessionId, subAgent
        );
    }

    private JChatMind buildJChatMindInstance(
            Agent agent,
            String systemPrompt,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId,
            String taskSessionId,
            boolean subAgent
    ) {
        ChatClient chatClient = chatClientRegistry.get(agent.getModel());
        if (Objects.isNull(chatClient)) {
            throw new IllegalStateException("未找到对应的 ChatClient: " + agent.getModel());
        }
        int memoryWindow = resolveMemoryWindow(taskSessionId, agentConfig, subAgent);
        McpToolBridge bridgeForResolver = CodingAgentRoles.isOrchestrator(agentConfig)
                || CodingAgentRoles.isReviewer(agentConfig) ? null : mcpToolBridge;
        JChatMind jChatMind = new JChatMind(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                systemPrompt,
                chatClient,
                memoryWindow,
                codingProperties.getAgent().isToolAwareMemory(),
                memory,
                toolCallbacks,
                knowledgeBases,
                chatSessionId,
                chatEventPublisher,
                eventBus,
                chatMessageFacadeService,
                chatMessageConverter,
                bridgeForResolver
        );
        jChatMind.setSchedulerMode(CodingAgentRoles.isOrchestrator(agentConfig));
        return jChatMind;
    }

    /**
     * Coding 任务或子 Agent 时使用 max(agent.messageLength, coding.agent.memory-window)。
     */
    private int resolveMemoryWindow(String sessionId, AgentDTO agentConfig, boolean subAgent) {
        Integer configured = agentConfig.getChatOptions() != null
                ? agentConfig.getChatOptions().getMessageLength()
                : null;
        int base = configured != null ? configured : AgentDTO.ChatOptions.defaultOptions().getMessageLength();
        if (subAgent || codingTaskService.getActiveTask(sessionId) != null) {
            return Math.max(base, codingProperties.getAgent().getMemoryWindow());
        }
        return base;
    }

    /**
     * 创建一个 JChatMind 实例
     */
    public JChatMind create(String agentId, String chatSessionId) {
        return create(agentId, chatSessionId, null);
    }

    /**
     * @param enrichedUserInput 若非空，替换记忆中最后一条用户消息（用于 @file 展开）
     */
    /**
     * 编排自动继续：在历史消息后追加一条 UserMessage，不替换最后一条用户消息。
     */
    public JChatMind createContinuation(String agentId, String chatSessionId, String continuationPrompt) {
        Agent agent = loadAgent(agentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = loadMemory(chatSessionId);
        memory.add(new UserMessage(continuationPrompt));

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig, chatSessionId);
        List<ToolCallback> toolCallbacks = toolRegistry.buildForSession(agentConfig, chatSessionId);
        return buildAgentRuntimeWithCodingSteps(
                agent, memory, knowledgeBases, toolCallbacks, chatSessionId);
    }

    public JChatMind create(String agentId, String chatSessionId, String enrichedUserInput) {
        Agent agent = loadAgent(agentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = loadMemory(chatSessionId, enrichedUserInput);

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig, chatSessionId);
        List<ToolCallback> toolCallbacks = toolRegistry.buildForSession(agentConfig, chatSessionId);
        return buildAgentRuntimeWithCodingSteps(
                agent,
                memory,
                knowledgeBases,
                toolCallbacks,
                chatSessionId
        );
    }

    /**
     * 创建子 Agent：独立 chatMemory（subSessionId），Coding 上下文与 SSE 绑定父会话。
     */
    /**
     * 为 spawn_agent 创建子 Agent：不按角色过滤工具，子 Agent 拥有 Agent 配置的全部工具。
     */
    public JChatMind createSpawnSubAgent(String agentId, String parentSessionId,
                                         String subSessionId, String goal) {
        Agent agent = loadAgent(agentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = new ArrayList<>();
        memory.add(new UserMessage(goal));

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig, parentSessionId);
        List<ToolCallback> toolCallbacks = toolRegistry.buildForSession(agentConfig, parentSessionId);

        JChatMind jChatMind = buildAgentRuntime(
                agent, memory, knowledgeBases, toolCallbacks, subSessionId, parentSessionId, true);
        jChatMind.setEventSessionId(parentSessionId);
        if (codingTaskService.getActiveTask(parentSessionId) != null) {
            jChatMind.setMaxSteps(codingSubagentProperties.getMaxLoopSteps());
        }
        return jChatMind;
    }

    public JChatMind createProfileSubAgent(AgentProfile profile, String parentSessionId,
                                             String subSessionId, String goal) {
        // Build AgentDTO directly from profile (skip Agent entity to avoid Assert checks)
        String agentId = "profile-" + profile.getName();
        AgentDTO cfg = AgentDTO.builder()
                .name(profile.getName())
                .description(profile.getDescription() != null ? profile.getDescription() : "")
                .systemPrompt(profile.getSystemPrompt() != null ? profile.getSystemPrompt() : "")
                .model(AgentDTO.ModelType.fromModelName("deepseek-chat"))
                .allowedTools(profile.getAllowedTools() != null ? new java.util.ArrayList<>(profile.getAllowedTools()) : new java.util.ArrayList<>())
                .allowedKbs(new java.util.ArrayList<>())
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();
        this.agentConfig = cfg;

        List<Message> memory = new ArrayList<>();
        memory.add(new UserMessage(goal));

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(cfg, parentSessionId);
        List<ToolCallback> toolCallbacks = toolRegistry.buildForProfile(profile);

        // Create coding task for worker sub-agent so file tools can function
        if ("worker".equals(profile.getName()) && parentSessionId != null) {
            CodingTask parentTask = codingTaskService.getActiveTask(parentSessionId);
            String wsRoot = null;
            String wsPath = ".";
            if (parentTask != null && parentTask.getWorkspaceRoot() != null) {
                wsRoot = parentTask.getWorkspaceRoot();
                wsPath = parentTask.getWorkspacePath() != null ? parentTask.getWorkspacePath() : ".";
            }
            if (wsRoot != null) {
                try {
                    codingTaskService.createTask(subSessionId, subSessionId, wsRoot, wsPath);
                } catch (Exception e) {
                    log.warn("Failed to create coding task for worker sub-agent: {}", e.getMessage());
                }
            }
        }

        // Build Agent entity for JChatMind constructor (required fields)
        Agent agent = Agent.builder()
                .id(agentId)
                .name(profile.getName())
                .description(profile.getDescription() != null ? profile.getDescription() : "")
                .systemPrompt(profile.getSystemPrompt() != null ? profile.getSystemPrompt() : "")
                .model("deepseek-chat")
                .allowedTools("[]")
                .allowedKbs("[]")
                .chatOptions("{}")
                .build();

        JChatMind jChatMind = buildAgentRuntimeWithCustomSystem(
                agent,
                profile.getSystemPrompt() != null ? profile.getSystemPrompt() : goal,
                memory, knowledgeBases, toolCallbacks, subSessionId, parentSessionId, true);
        jChatMind.setEventSessionId(parentSessionId);
        jChatMind.setMaxSteps(profile.getMaxSteps() > 0 ? profile.getMaxSteps() : 35);
        return jChatMind;
    }

    public JChatMind createSubAgent(String workerAgentId, String parentSessionId,
                                    String subSessionId, String goal) {
        Agent agent = loadAgent(workerAgentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = new ArrayList<>();
        memory.add(new UserMessage(goal));

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig, parentSessionId);
        List<ToolCallback> toolCallbacks = toolRegistry.buildForSession(agentConfig, parentSessionId);
        JChatMind jChatMind = buildAgentRuntime(
                agent, memory, knowledgeBases, toolCallbacks, subSessionId, parentSessionId, true);
        jChatMind.setEventSessionId(parentSessionId);
        if (codingTaskService.getActiveTask(parentSessionId) != null) {
            jChatMind.setMaxSteps(codingSubagentProperties.getMaxLoopSteps());
        }
        return jChatMind;
    }

    /**
     * Coding Worker 会话内注入 MCP shell 工具。
     * Orchestrator 仅委派，不得获得终端工具（避免 Worker 失败后父 Agent 亲自改代码）。
     */
    private JChatMind buildAgentRuntimeWithCodingSteps(
            Agent agent,
            List<Message> memory,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks,
            String chatSessionId
    ) {
        JChatMind jChatMind = buildAgentRuntime(agent, memory, knowledgeBases, toolCallbacks, chatSessionId);
        if (codingTaskService.getActiveTask(chatSessionId) != null) {
            jChatMind.setMaxSteps(codingProperties.getAgent().getMaxLoopSteps());
        }
        return jChatMind;
    }
}
