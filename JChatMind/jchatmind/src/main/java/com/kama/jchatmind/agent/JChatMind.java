package com.kama.jchatmind.agent;

import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.realtime.ChatEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import com.kama.jchatmind.agent.memory.ToolAwareMessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import com.kama.jchatmind.mcp.bridge.AliasAwareToolCallbackResolver;
import com.kama.jchatmind.mcp.bridge.McpToolAliasRegistry;
import com.kama.jchatmind.mcp.bridge.McpToolBridge;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class JChatMind {
    // 智能体 ID
    private String agentId;

    // 名称
    private String name;

    // 描述
    private String description;

    // 默认系统提示词
    private String systemPrompt;

    // 交互实例
    private ChatClient chatClient;

    // 状态
    private AgentState agentState;

    // 可用的工具
    private List<ToolCallback> availableTools;

    // 可访问的知识库
    private List<KnowledgeBaseDTO> availableKbs;

    // 工具调用管理器
    private ToolCallingManager toolCallingManager;

    // 模型的聊天记录
    private ChatMemory chatMemory;

    // 模型的聊天会话 ID
    private String chatSessionId;

    /** SSE 推送目标会话；子 Agent 时指向父会话 */
    private String eventSessionId;

    // 最多循环次数
    private int maxSteps = MAX_STEPS;

    private static final Integer MAX_STEPS = 20;

    private static final Integer DEFAULT_MAX_MESSAGES = 20;

    // SpringAI 自带的 ChatOptions, 不是 AgentDTO.ChatOptions
    private ChatOptions chatOptions;

    // 实时事件发布（local SSE 或 RocketMQ→SSE）
    private ChatEventPublisher chatEventPublisher;

    private ChatMessageConverter chatMessageConverter;

    private ChatMessageFacadeService chatMessageFacadeService;

    // 最后一次的 ChatResponse
    private ChatResponse lastChatResponse;

    // AI 返回的，已经持久化，但是需要 sse 发给前端的消息
    private final List<ChatMessageDTO> pendingChatMessages = new ArrayList<>();

    public JChatMind() {
    }

    public JChatMind(String agentId,
                     String name,
                     String description,
                     String systemPrompt,
                     ChatClient chatClient,
                     Integer maxMessages,
                     boolean useToolAwareMemory,
                     List<Message> memory,
                     List<ToolCallback> availableTools,
                     List<KnowledgeBaseDTO> availableKbs,
                     String chatSessionId,
                     ChatEventPublisher chatEventPublisher,
                     ChatMessageFacadeService chatMessageFacadeService,
                     ChatMessageConverter chatMessageConverter,
                     McpToolBridge mcpToolBridge
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;

        this.chatClient = chatClient;

        this.availableKbs = availableKbs;

        this.chatSessionId = chatSessionId;
        this.chatEventPublisher = chatEventPublisher;

        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;

        this.maxSteps = MAX_STEPS;

        this.agentState = AgentState.IDLE;

        // 保存聊天记录（Tool-Aware 按轮裁剪，避免 assistant/tool 链被条数窗口裁断）
        int effMaxMessages = maxMessages == null ? DEFAULT_MAX_MESSAGES : maxMessages;
        if (useToolAwareMemory) {
            this.chatMemory = ToolAwareMessageWindowChatMemory.builder()
                    .maxMessages(effMaxMessages)
                    .pinSystemMessage(true)
                    .pinFirstUserMessage(true)
                    .build();
        } else {
            this.chatMemory = MessageWindowChatMemory.builder()
                    .maxMessages(effMaxMessages)
                    .build();
        }
        this.chatMemory.add(chatSessionId, memory);

        // 添加系统提示
        if (StringUtils.hasLength(systemPrompt)) {
            this.chatMemory.add(chatSessionId, new SystemMessage(systemPrompt));
        }

        // 关闭 SpringAI 自带的内部的工具调用自动执行功能
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();

        // 展开 MCP 别名并统一解析（精确名 / 别名 / 连接前缀），避免 run_terminal_cmd 等名称找不到回调
        this.availableTools = McpToolAliasRegistry.expandAliases(availableTools);
        this.toolCallingManager = ToolCallingManager.builder()
                .toolCallbackResolver(new AliasAwareToolCallbackResolver(this.availableTools, mcpToolBridge))
                .build();
    }

    /**
     * 条数窗口裁剪后仍可能出现不完整 tool 链（如回退 MessageWindowChatMemory 或历史损坏）。
     * DeepSeek/OpenAI 要求 tool 必须紧接在带 tool_calls 的 assistant 之后。
     * 发送给模型前做一次清理：去掉悬空 tool、去掉未跟齐 tool 的 assistant 等。
     */
    private List<Message> sanitizeMessagesForToolCallingApi(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }
        List<Message> out = new ArrayList<>();
        Set<String> pendingToolCallIds = new LinkedHashSet<>();

        for (Message message : messages) {
            if (message instanceof ToolResponseMessage tr) {
                if (pendingToolCallIds.isEmpty()) {
                    log.warn("已丢弃悬空的 tool 消息（窗口裁剪或历史损坏会导致此类片段）");
                    continue;
                }
                Set<String> responseIds = tr.getResponses().stream()
                        .map(ToolResponseMessage.ToolResponse::id)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (responseIds.isEmpty() && pendingToolCallIds.size() == 1) {
                    out.add(tr);
                    pendingToolCallIds.clear();
                    continue;
                }
                if (responseIds.isEmpty() || !pendingToolCallIds.containsAll(responseIds)) {
                    log.warn("tool 消息的 tool_call_id 与当前待响应 id 不一致，已跳过: responseIds={}, pending={}",
                            responseIds, pendingToolCallIds);
                    continue;
                }
                out.add(tr);
                pendingToolCallIds.removeAll(responseIds);
                continue;
            }

            if (message instanceof AssistantMessage am && !CollectionUtils.isEmpty(am.getToolCalls())) {
                if (!pendingToolCallIds.isEmpty()) {
                    removeIncompleteToolCallingRound(out);
                    pendingToolCallIds.clear();
                }
                pendingToolCallIds.addAll(am.getToolCalls().stream()
                        .map(AssistantMessage.ToolCall::id)
                        .filter(StringUtils::hasText)
                        .toList());
                out.add(am);
                continue;
            }

            if (!pendingToolCallIds.isEmpty()) {
                removeIncompleteToolCallingRound(out);
                pendingToolCallIds.clear();
            }
            out.add(message);
        }

        if (!pendingToolCallIds.isEmpty()) {
            log.warn("对话末尾存在未完成的 tool_calls，已从发给模型的历史中移除该轮 assistant");
            removeIncompleteToolCallingRound(out);
        }
        return out;
    }

    private static void removeIncompleteToolCallingRound(List<Message> out) {
        while (!out.isEmpty() && out.get(out.size() - 1) instanceof ToolResponseMessage) {
            out.remove(out.size() - 1);
        }
        if (!out.isEmpty() && out.get(out.size() - 1) instanceof AssistantMessage lastAsst
                && !CollectionUtils.isEmpty(lastAsst.getToolCalls())) {
            out.remove(out.size() - 1);
        }
    }

    private List<Message> messagesForPrompt() {
        return sanitizeMessagesForToolCallingApi(this.chatMemory.get(this.chatSessionId));
    }

    // 打印工具调用信息
    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("\n\n[ToolCalling] 无工具调用");
            return;
        }
        String logMessage = IntStream.range(0, toolCalls.size())
                .mapToObj(i -> {
                    AssistantMessage.ToolCall call = toolCalls.get(i);
                    return String.format(
                            "[ToolCalling #%d]\n- name      : %s\n- arguments : %s",
                            i + 1,
                            call.name(),
                            call.arguments()
                    );
                })
                .collect(Collectors.joining("\n\n"));
        log.info("\n\n========== Tool Calling ==========\n{}\n=================================\n", logMessage);
    }

    // 持久化 Message, 返回 chatMessageId
    // 需要 Agent 持久化的 Message 子类有以下两类
    // AssistantMessage
    // ToolResponseMessage

    // SystemMessage 不需要持久化
    // UserMessage 在每次用户发送问题之间就已经持久化过了
    private void saveMessage(Message message) {
        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder();
        if (message instanceof AssistantMessage assistantMessage) {
            ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.ASSISTANT)
                    .content(assistantMessage.getText())
                    .sessionId(this.chatSessionId)
                    .metadata(ChatMessageDTO.MetaData.builder()
                            .toolCalls(assistantMessage.getToolCalls())
                            .build())
                    .build();
            CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
            chatMessageDTO.setId(chatMessage.getChatMessageId());
            pendingChatMessages.add(chatMessageDTO);
        } else if (message instanceof ToolResponseMessage toolResponseMessage) {
            // 持久化 ToolResponseMessage
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.TOOL)
                        .content(toolResponse.responseData())
                        .sessionId(this.chatSessionId)
                        .metadata(ChatMessageDTO.MetaData.builder()
                                .toolResponse(toolResponse)
                                .build())
                        .build();
                CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
                chatMessageDTO.setId(chatMessage.getChatMessageId());
                pendingChatMessages.add(chatMessageDTO);
            }
        } else {
            throw new IllegalArgumentException("不支持的 Message 类型: " + message.getClass().getName());
        }
    }

    // 刷新 pendingMessages, 将数据通过 sse 发送给前端
    private void refreshPendingMessages() {
        for (ChatMessageDTO message : pendingChatMessages) {
            ChatMessageVO vo = chatMessageConverter.toVO(message);
            SseMessage sseMessage = SseMessage.builder()
                    .type(SseMessage.Type.AI_GENERATED_CONTENT)
                    .payload(SseMessage.Payload.builder()
                            .message(vo)
                            .build())
                    .metadata(SseMessage.Metadata.builder()
                            .chatMessageId(message.getId())
                            .build())
                    .build();
            chatEventPublisher.publish(resolveEventSessionId(), sseMessage);
        }
        pendingChatMessages.clear();
    }

    private String resolveEventSessionId() {
        return eventSessionId != null ? eventSessionId : chatSessionId;
    }

    public void setEventSessionId(String eventSessionId) {
        this.eventSessionId = eventSessionId;
    }

    private void emitAgentPhase(SseMessage.Type type, String statusText) {
        if (this.chatEventPublisher == null) {
            return;
        }
        this.chatEventPublisher.publish(resolveEventSessionId(), SseMessage.builder()
                .type(type)
                .payload(SseMessage.Payload.builder()
                        .statusText(statusText)
                        .done(false)
                        .build())
                .build());
    }

    private static String truncateForStatus(String text, int maxLen) {
        if (!StringUtils.hasText(text) || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen) + "…";
    }

    /**
     * 推理阶段：基于对话历史与上一轮工具观察结果，决定下一步动作或给出最终回复。
     *
     * @return 若模型请求工具调用则返回 true，否则表示任务在本轮结束
     */
    private boolean reason() {
        String reasonPrompt =
                """
                你处于 Agent「推理 → 工具调用 → 结果观察 → 继续推理」闭环的【推理】阶段。
                请根据当前对话上下文（含上一轮工具返回的观察结果）：
                1. 简要分析当前进展与尚未完成的部分
                2. 若仍需信息或操作，调用相应工具（可连续多步，勿中途停下来等用户再发消息）
                3. 若已满足用户的完整请求，直接给出最终回复，不要再调用工具
                4. 缺少必要信息时优先调用工具获取，不要向用户追问
                【额外信息】
                - 你目前拥有的知识库列表以及描述：%s
                - 如果有缺失的上下文时，优先从知识库中进行搜索
                """.formatted(this.availableKbs);

        Prompt prompt = Prompt.builder()
                .chatOptions(this.chatOptions)
                .messages(messagesForPrompt())
                .build();

        this.lastChatResponse = this.chatClient
                .prompt(prompt)
                .system(reasonPrompt)
                .toolCallbacks(this.availableTools.toArray(new ToolCallback[0]))
                .call()
                .chatClientResponse()
                .chatResponse();

        Assert.notNull(lastChatResponse, "Last chat client response cannot be null");

        AssistantMessage output = this.lastChatResponse
                .getResult()
                .getOutput();

        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        if (StringUtils.hasText(output.getText())) {
            emitAgentPhase(SseMessage.Type.AI_THINKING,
                    truncateForStatus(output.getText(), 200));
        }

        // 无工具调用：直接持久化并推送本轮 assistant 回复。
        // 有工具调用：不要在此处持久化，否则 DB 中会出现「带 tool_calls 的 assistant」却尚未有条目对应的
        // tool 消息；且若结合错误的「最近消息」加载逻辑，传给模型的历史会缺少 tool 回复，触发 400。
        if (toolCalls.isEmpty()) {
            saveMessage(output);
            refreshPendingMessages();
        }

        logToolCalls(toolCalls);

        return !toolCalls.isEmpty();
    }

    /**
     * 工具调用阶段：执行模型在本轮推理中请求的工具。
     */
    private ToolResponseMessage act() {
        Assert.notNull(this.lastChatResponse, "Last chat client response cannot be null");
        Assert.isTrue(this.lastChatResponse.hasToolCalls(), "act() 需要带 tool_calls 的 assistant 响应");

        Prompt prompt = Prompt.builder()
                .messages(messagesForPrompt())
                .chatOptions(this.chatOptions)
                .build();

        ToolExecutionResult toolExecutionResult;
        try {
            toolExecutionResult = toolCallingManager.executeToolCalls(prompt, this.lastChatResponse);
        } catch (Exception e) {
            if (isToolArgumentParseFailure(e)) {
                log.warn("工具参数 JSON 解析失败，已转为可恢复提示供 Agent 重试: {}", e.getMessage());
                return recoverFromToolArgumentParseFailure(e);
            }
            throw e;
        }

        this.chatMemory.clear(this.chatSessionId);
        this.chatMemory.add(this.chatSessionId, toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult
                .conversationHistory()
                .get(toolExecutionResult.conversationHistory().size() - 1);

        // 先持久化带 tool_calls 的 assistant，再持久化各 tool 消息，保证顺序与 OpenAI 约定一致
        saveMessage(this.lastChatResponse.getResult().getOutput());
        saveMessage(toolResponseMessage);
        refreshPendingMessages();

        return toolResponseMessage;
    }

    private ToolResponseMessage recoverFromToolArgumentParseFailure(Throwable e) {
        AssistantMessage output = this.lastChatResponse.getResult().getOutput();
        String hint = """
                工具参数 JSON 无效（常见于单次 write_coding_file 写入超大 HTML/JS，模型输出被截断或引号未转义）。
                请分步写入，勿在一次工具调用中塞入完整页面/游戏：
                1. write_coding_file 先写简短骨架（DOCTYPE + head + 空 body，建议 <2000 字符）
                2. append_coding_file 或 apply_coding_patch 分块追加 style/script
                3. 单次 content 建议不超过 8000 字符
                原始错误: %s""".formatted(rootCauseMessage(e));

        List<ToolResponseMessage.ToolResponse> responses = output.getToolCalls().stream()
                .map(tc -> new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), hint))
                .toList();
        ToolResponseMessage toolResponseMessage = ToolResponseMessage.builder()
                .responses(responses)
                .build();

        List<Message> history = new ArrayList<>(this.chatMemory.get(this.chatSessionId));
        history.add(output);
        history.add(toolResponseMessage);
        this.chatMemory.clear(this.chatSessionId);
        this.chatMemory.add(this.chatSessionId, history);

        saveMessage(output);
        saveMessage(toolResponseMessage);
        refreshPendingMessages();
        return toolResponseMessage;
    }

    private static boolean isToolArgumentParseFailure(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof JsonParseException || t instanceof JsonMappingException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Unexpected end-of-input")
                    || msg.contains("was expecting closing quote")
                    || msg.contains("JsonParseException")
                    || msg.contains("JsonMappingException"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    /**
     * 观察阶段：汇总工具返回，供前端展示；结果已写入 chatMemory，下一轮 reason() 会继续推理。
     */
    private void observe(ToolResponseMessage toolResponseMessage) {
        String observationSummary = toolResponseMessage.getResponses()
                .stream()
                .map(resp -> resp.name() + ": " + truncateForStatus(resp.responseData(), 120))
                .collect(Collectors.joining(" | "));

        log.info("工具观察结果：{}", observationSummary);
        emitAgentPhase(SseMessage.Type.AI_OBSERVING,
                truncateForStatus(observationSummary, 240));

        if (toolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().equals("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("任务结束");
        }
    }

    private String formatActStatus(List<AssistantMessage.ToolCall> toolCalls) {
        String toolNames = toolCalls.stream()
                .map(AssistantMessage.ToolCall::name)
                .collect(Collectors.joining(", "));
        return "调用工具: " + toolNames;
    }

    /**
     * Agent 闭环单步：推理 → 工具调用 → 结果观察 →（下一轮继续推理）
     */
    private void loopStep(int stepIndex) {
        this.agentState = AgentState.THINKING;
        emitAgentPhase(SseMessage.Type.AI_THINKING,
                "第 " + stepIndex + " 轮 · 推理中…");

        if (!reason()) {
            this.agentState = AgentState.FINISHED;
            return;
        }

        List<AssistantMessage.ToolCall> toolCalls = this.lastChatResponse.getResult().getOutput().getToolCalls();
        this.agentState = AgentState.EXECUTING;
        emitAgentPhase(SseMessage.Type.AI_EXECUTING, formatActStatus(toolCalls));

        ToolResponseMessage toolResponseMessage = act();

        this.agentState = AgentState.OBSERVING;
        observe(toolResponseMessage);
    }

    public void setMaxSteps(int maxSteps) {
        if (maxSteps > 0) {
            this.maxSteps = maxSteps;
        }
    }

    // 运行
    public void run() {
        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent is not idle");
        }

        try {
            this.agentState = AgentState.PLANNING;
            emitAgentPhase(SseMessage.Type.AI_PLANNING, "分析任务并规划执行步骤");

            for (int i = 0; i < maxSteps && agentState != AgentState.FINISHED; i++) {
                loopStep(i + 1);
                if (i + 1 >= maxSteps) {
                    agentState = AgentState.FINISHED;
                    log.warn("Max steps reached, stopping agent");
                }
            }
            agentState = AgentState.FINISHED;
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Error running agent", e);
            throw new RuntimeException(formatAgentFailureMessage(e), e);
        } finally {
            emitAgentPhase(SseMessage.Type.AI_DONE,
                    agentState == AgentState.ERROR ? "执行出错" : "处理完成");
        }
    }

    private static String formatAgentFailureMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        if (detail != null && !detail.isBlank()) {
            return "Error running agent: " + detail;
        }
        return "Error running agent: " + root.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return "JChatMind {" +
                "name = " + name + ",\n" +
                "description = " + description + ",\n" +
                "agentId = " + agentId + ",\n" +
                "systemPrompt = " + systemPrompt + "}";
    }
}
