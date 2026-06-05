package com.kama.jchatmind.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.coding.config.CodingProperties;
import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import com.kama.jchatmind.coding.model.entity.CodingTask;
import com.kama.jchatmind.coding.service.CodingTaskService;
import com.kama.jchatmind.coding.service.CodingPromptComposer;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import com.kama.jchatmind.mcp.integration.McpIntegration;
import com.kama.jchatmind.mcp.integration.McpIntegrationImpl;
import com.kama.jchatmind.memory.config.MemoryProperties;
import com.kama.jchatmind.memory.integration.MemoryIntegration;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import com.kama.jchatmind.service.ToolFacadeService;
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
    private final CodingPromptComposer codingPromptComposer;
    private final CodingTaskService codingTaskService;
    private final CodingProperties codingProperties;
    private final CodingSubagentProperties codingSubagentProperties;

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
            CodingPromptComposer codingPromptComposer,
            CodingTaskService codingTaskService,
            CodingProperties codingProperties,
            CodingSubagentProperties codingSubagentProperties
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
        this.codingPromptComposer = codingPromptComposer;
        this.codingTaskService = codingTaskService;
        this.codingProperties = codingProperties;
        this.codingSubagentProperties = codingSubagentProperties;
    }

    private String buildSystemPromptWithRules(String basePrompt, String chatSessionId) {
        return codingPromptComposer.composeSystemPrompt(basePrompt, chatSessionId);
    }

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
        if (memoryProperties.isEnabled()) {
            try {
                List<Message> supplemental = memoryIntegration.buildSupplementalContext(chatSessionId, 0);
                if (supplemental != null && !supplemental.isEmpty()) {
                    List<Message> merged = new ArrayList<>();
                    merged.add(new SystemMessage("以下是与当前会话相关的历史记忆（来自 Memory Hub）："));
                    merged.addAll(supplemental);
                    merged.addAll(memory);
                    memory = merged;
                    log.debug("Memory Hub 已为 session={} 注入 {} 条补充记忆", chatSessionId, supplemental.size());
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
        return memory;
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
        List<String> allowedKbIds = agentConfig.getAllowedKbs();
        if (allowedKbIds == null || allowedKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectByIdBatch(allowedKbIds);
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

    private List<Tool> resolveRuntimeTools(AgentDTO agentConfig) {
        // 固定工具（系统强制）
        List<Tool> runtimeTools = new ArrayList<>(toolFacadeService.getFixedTools());

        // 可选工具（按 Agent 配置）
        List<String> allowedToolNames = agentConfig.getAllowedTools();
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return runtimeTools;
        }

        Map<String, Tool> optionalToolMap = toolFacadeService.getOptionalTools()
                .stream()
                .collect(Collectors.toMap(Tool::getName, Function.identity()));

        for (String toolName : allowedToolNames) {
            Tool tool = optionalToolMap.get(toolName);
            if (tool != null) {
                runtimeTools.add(tool);
            }
        }
        return runtimeTools;
    }

    private List<ToolCallback> buildToolCallbacks(List<Tool> runtimeTools) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : runtimeTools) {
            Object target = resolveToolTarget(tool);
            ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(target)
                    .build()
                    .getToolCallbacks();
            callbacks.addAll(Arrays.asList(toolCallbacks));
        }
        return callbacks;
    }

    private Object resolveToolTarget(Tool tool) {
        try {
            return AopUtils.isAopProxy(tool)
                    ? AopUtils.getTargetClass(tool)
                    : tool;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "解析工具目标对象失败: " + tool.getName(), e);
        }
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
        ChatClient chatClient = chatClientRegistry.get(agent.getModel());
        if (Objects.isNull(chatClient)) {
            throw new IllegalStateException("未找到对应的 ChatClient: " + agent.getModel());
        }
        int memoryWindow = resolveMemoryWindow(taskSessionId, agentConfig, subAgent);
        return new JChatMind(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                buildSystemPromptWithRules(agent.getSystemPrompt(), taskSessionId),
                chatClient,
                memoryWindow,
                codingProperties.getAgent().isToolAwareMemory(),
                memory,
                toolCallbacks,
                knowledgeBases,
                chatSessionId,
                chatEventPublisher,
                chatMessageFacadeService,
                chatMessageConverter
        );
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
    public JChatMind create(String agentId, String chatSessionId, String enrichedUserInput) {
        Agent agent = loadAgent(agentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = loadMemory(chatSessionId, enrichedUserInput);

        // 解析 agent 的支持的知识库
        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig);
        // 解析 agent 支持的工具调用
        List<Tool> runtimeTools = resolveRuntimeTools(agentConfig);
        // 将工具调用转换成 ToolCallback 的形式
        List<ToolCallback> toolCallbacks = buildToolCallbacks(runtimeTools);

        // MCP opt-in：启用时按 allowedTools 白名单追加 MCP 工具，失败/无连接时静默跳过
        if (mcpProperties.isEnabled()) {
            try {
                List<ToolCallback> mcpTools = mcpIntegration.getToolsForAgent(agentConfig.getAllowedTools());
                if (!mcpTools.isEmpty()) {
                    toolCallbacks.addAll(mcpTools);
                    log.info("已为 Agent[{}] 注入 {} 个 MCP 工具: {}", agentId, mcpTools.size(),
                            mcpTools.stream().map(t -> t.getToolDefinition().name()).toList());
                } else if (agentConfig.getAllowedTools() != null
                        && agentConfig.getAllowedTools().stream().anyMatch(McpIntegrationImpl::isTerminalToolName)) {
                    log.warn("Agent[{}] 白名单含终端工具但未发现 MCP 工具，请检查 mcp-proxy 与 spring.ai.mcp.client",
                            agentId);
                }
            } catch (Exception e) {
                log.warn("注入 MCP 工具失败，已跳过: {}", e.getMessage());
            }
        }

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
    public JChatMind createSubAgent(String workerAgentId, String parentSessionId,
                                    String subSessionId, String goal) {
        Agent agent = loadAgent(workerAgentId);
        AgentDTO agentConfig = toAgentConfig(agent);
        List<Message> memory = new ArrayList<>();
        memory.add(new UserMessage(goal));

        List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig);
        List<Tool> runtimeTools = filterWorkerTools(resolveRuntimeTools(agentConfig));
        List<ToolCallback> toolCallbacks = buildToolCallbacks(runtimeTools);

        if (mcpProperties.isEnabled()) {
            try {
                List<ToolCallback> mcpTools = mcpIntegration.getToolsForAgent(agentConfig.getAllowedTools());
                if (!mcpTools.isEmpty()) {
                    toolCallbacks.addAll(mcpTools);
                    log.info("已为子 Agent[{}] 注入 {} 个 MCP 工具", workerAgentId, mcpTools.size());
                }
            } catch (Exception e) {
                log.warn("子 Agent 注入 MCP 工具失败，已跳过: {}", e.getMessage());
            }
        }

        JChatMind jChatMind = buildAgentRuntime(
                agent, memory, knowledgeBases, toolCallbacks, subSessionId, parentSessionId, true);
        jChatMind.setEventSessionId(parentSessionId);
        if (codingTaskService.getActiveTask(parentSessionId) != null) {
            jChatMind.setMaxSteps(codingSubagentProperties.getMaxLoopSteps());
        }
        return jChatMind;
    }

    private List<Tool> filterWorkerTools(List<Tool> tools) {
        Set<String> blocked = Set.of(
                "delegate_coding_task",
                "coding_subtask_tools"
        );
        return tools.stream()
                .filter(t -> !blocked.contains(t.getName()))
                .toList();
    }

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
