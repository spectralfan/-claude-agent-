## JChatMind 软件架构分析

### 1. 整体架构概览

JChatMind 是基于 **Spring Boot 3.5.x** + **Spring AI 1.1.x** 的多智能体对话系统，核心能力包括：

- **对话编排**：基于 `JChatMind` 的 Think–Execute 循环，支持 Tool Calling、多轮对话。
- **记忆系统（Memory Hub）**：分层记忆（WORKING / RECENT / ARCHIVE），支持重要性评估、向量召回、离线评测。
- **MCP 工具系统**：通过 Spring AI MCP 客户端接入外部 Model Context Protocol 服务器，将 MCP 工具透明暴露为本地工具。
- **知识库 & RAG**：知识库实体与检索服务（未在本文件详细展开）。
- **基础设施**：PostgreSQL + MyBatis，pgvector 支持向量检索，异步与定时任务、SSE 推送前端。

整体逻辑可抽象为：

1. 前端发送消息 → 经 HTTP 接口持久化为 `chat_message`。
2. `JChatMindFactory` 基于 Agent 配置创建 `JChatMind` 实例。
3. `JChatMind` 读取记忆（Memory Hub 或 chat_message 历史），注入工具（本地 Tool + MCP Tool）。
4. 执行 Think–Execute 循环：模型决策 → 可能调用工具 → 保存结果和记忆 → SSE 推送前端。

### 2. 核心模块分层

#### 2.1 Agent 层（对话与决策）

- `agent/JChatMind`：单个 Agent 的运行时对象，封装完整的对话循环。
  - 使用 `ChatClient`（来自 `ChatClientRegistry`）与 LLM 通信。
  - 使用 `MessageWindowChatMemory` 存储有限窗口内的历史消息。
  - 使用 `ToolCallingManager` 执行工具调用（模型仅决策，具体执行由本地逻辑控制）。
  - 将 `AssistantMessage` / `ToolResponseMessage` 通过 `ChatMessageFacadeService` 持久化，再经 `SseService` 推送到前端。
- `agent/JChatMindFactory`：Agent 工厂，负责：
  - 从数据库加载 `Agent` 实体，并转换为 `AgentDTO`（通过 `AgentConverter`）。
  - 基于 `chatSessionId` 加载历史消息：
    - 优先使用 Memory Hub（`MemoryIntegration.buildContext`），为空时退回 `chat_message` 历史。
  - 按 Agent 配置解析可用知识库（`KnowledgeBaseMapper` + `KnowledgeBaseConverter`）。
  - 按 Agent 配置解析可用工具（`ToolFacadeService` 提供本地 Tool 集合）。
  - 将所有工具转换为 Spring AI `ToolCallback` 注入 `JChatMind`。
  - 在 Memory/MCP 开关下，决定是否加载分层记忆和 MCP 工具。

#### 2.2 工具系统（本地 Tool）

- `agent.tools.Tool`：本地工具接口（带 name/description/type）。
- 各类工具实现位于 `agent.tools.*`（如 `WeatherTool`、`FileSystemTools`、`KnowledgeTools` 等），通过：
  - `@Component` 注入 Spring 容器。
  - 方法上使用 `@org.springframework.ai.tool.annotation.Tool` 描述给 LLM 的工具签名与参数。
- `ToolFacadeService`：
  - 收集所有实现 `Tool` 接口的 Bean。
  - 根据 `ToolType.FIXED/OPTIONAL` 提供固定/可选工具集合。
- `JChatMindFactory` 使用 `MethodToolCallbackProvider` 将工具实例转换为 Spring AI `ToolCallback`。

#### 2.3 记忆系统（Memory Hub）

位于 `memory/` 包，包括：

- **配置**：
  - `MemoryProperties`（`memory.hub.*`）：是否启用、embedding 模型名称、维度、窗口大小、重要性阈值、整理任务相关参数等。
- **实体与 Mapper**：
  - 实体：`MemoryEntry` / `MemoryEmbedding` / `MemoryContext` / `MemorySession` / `MemoryStats` / `MemoryTask`。
  - Mapper 接口 + XML：`MemoryEntryMapper` 等，对应 `t_memory_*` 表，使用：
    - `TEXT[]` → `List<String>`（`StringListTypeHandler`）。
    - JSONB 列通过 `CAST(#{json} AS jsonb)`，在实体中作为 `String` 承载。
    - 时间列使用 `TIMESTAMP` 配合 `LocalDateTime`。
- **服务层**：
  - `WorkingMemoryService` / `RecentMemoryService` / `ArchiveMemoryService`：
    - 短期（WORKING）：滑动窗口，记录原始对话内容。
    - 中期（RECENT）：通过 `MemoryImportanceService` 打分，提取关键对话。
    - 长期（ARCHIVE）：生成摘要并向量化（通过本地 Ollama `bge-m3` + `pgvector`）。
  - `MemorySelector`：分层召回策略：
    - WORKING：最近 N 条（时间窗口）。
    - RECENT：按重要性排序取 Top。
    - ARCHIVE：基于向量相似度检索相关记忆。
  - `MemoryService`：对外统一接口，封装保存、查询、会话管理、整理触发等操作。
- **Agent 集成**：
  - `memory.integration.MemoryIntegration`：
    - `buildContext(sessionId, maxTokens)`：为 Agent 构建上下文消息列表。
    - 提供工具调用/用户确认/会话结束等事件的记忆同步。
  - `JChatMindFactory.loadMemory`：
    - 当 `memory.hub.enabled=true` 时优先调用 `MemoryIntegration.buildContext`。
    - 若 Memory Hub 当前无记忆，再回退到老的 `chat_message` 表加载逻辑，保证向后兼容。
- **任务与异步整理**：
  - `MemoryTaskService` + `MemoryAgent`：调度与执行记忆整理任务（生成摘要 + 向量化 + 清理）。
  - `AsyncConfig` 启用了 `@EnableAsync` + `@EnableScheduling`，支持异步与定时任务。
- **离线评测**（位于 `src/test`）：
  - `eval/*`：`EvalDataset`、`MemoryMetrics`、`LlmJudge`、`EvalReport`、`MemoryEvaluationIT`。
  - 利用合成数据集 `dataset.json` 对 Memory Hub 的召回率、重复率、清晰率、tag 边精确率与召回有用性进行离线评测。

#### 2.4 MCP 模块（Model Context Protocol）

位于 `mcp/` 包，基于 **Spring AI MCP Client** 与外部 `mcp-proxy-server` 聚合网关集成：

- **连接层（Spring AI 提供）**：
  - 通过 `spring-ai-starter-mcp-client` 自动装配 `McpSyncClient`。
  - `application.yaml` 中的 `spring.ai.mcp.client.*` 配置：
    - `enabled`、`name`、`version`、`type=SYNC`、`request-timeout`，
    - `sse.connections.proxy.url` / `sse-endpoint` 指向 mcp-proxy-server。
  - 禁用 Spring AI 自带的 ToolCallback 自动集成（`toolcallback.enabled=false`），由本项目 MCP 模块手动接管。

- **自有配置与实体**：
  - `McpProperties`（`mcp.*`）：
    - `enabled`：是否把 MCP 工具注入 Agent。
    - `record-calls`：是否记录调用到 `t_mcp_tool_call`。
    - `tool-name-prefix-strip`：是否对工具名做前缀剥离兜底匹配（解决 SDK 可能加的前缀）。
  - `McpToolCall` + `McpCallStatus` + `McpCallResultDTO`：
    - 对应数据库表 `t_mcp_tool_call`，记录 serverId、toolName、参数、结果、错误、耗时、会话与 Agent 信息。

- **持久化层**：
  - `McpToolCallMapper` + `McpToolCallMapper.xml`：
    - `insert(McpToolCall)`：将调用记录写入 `t_mcp_tool_call`，JSON 字段以 String 承载并 `CAST(... AS jsonb)`。
    - `selectHistory(serverId, toolName, since, limit)`：查询调用历史。
    - `usageStats(serverId)`：按 `tool_name` 聚合统计调用次数。

- **桥接与埋点**：
  - `McpToolBridge` / `McpToolBridgeImpl`：
    - 注入 `ObjectProvider<McpSyncClient>`，遍历所有 MCP 连接。
    - 用 `SyncMcpToolCallbackProvider` 将各 MCP client 的工具转为 Spring AI `ToolCallback`。
    - 对每个 MCP 工具回调包装一层 `RecordingToolCallback` 用于埋点。
  - `RecordingToolCallback`：
    - 实现 `ToolCallback`，内部委托真实 MCP 工具执行。
    - 记录执行耗时与状态（SUCCESS/FAILED）。
    - 异常时不向外抛出，而是返回带错误信息的工具输出字符串，避免中断 Agent 循环。
  - `McpCallRecorder`：
    - `@Component` + `@Async`，负责异步写入 `t_mcp_tool_call`。
    - 对参数/结果做 JSON 安全性处理（合法 JSON 直接存储，否则包装为 JSON 字符串）。

- **与 Agent 的集成**：
  - `McpIntegration` / `McpIntegrationImpl`：
    - `getToolsForAgent(allowedToolNames)`：根据 Agent 配置的 `allowedTools` 白名单筛选 MCP 工具。
      - 先精确匹配工具名。
      - 在启用 `tool-name-prefix-strip` 时，对 `alt_1_xxx` 等带前缀的名字按最后一个下划线拆分做兜底匹配。
    - `getCallHistory` / `getToolUsageStats`：提供调用历史与用量统计的只读接口。
  - `JChatMindFactory` 中的集成：
    - 注入 `McpProperties` 与 `McpIntegration`。
    - 在构建 `toolCallbacks`（本地工具）之后：
      - 若 `mcp.enabled=true`，则调用 `mcpIntegration.getToolsForAgent(agentConfig.getAllowedTools())`，将返回的 MCP 工具回调追加到 Agent 的 `availableTools` 列表。
      - 连接失败/无 MCP 客户端/筛选为空时，静默跳过，保证 Agent 行为与关闭 MCP 时一致。

#### 2.5 数据访问与基础设施

- **数据库**：PostgreSQL
  - 核心业务表（未在此枚举）+ Memory Hub 表 `t_memory_*` + MCP 调用记录表 `t_mcp_tool_call`。
  - 使用 `pgvector` 扩展支持向量相似度检索，Memory Hub 中 `t_memory_embedding.embedding VECTOR(1024)` 与 Ollama `bge-m3` 模型维度一致。
- **持久化框架**：MyBatis + XML Mapper
  - Mapper 接口位于 `*.mapper` 包；XML 文件位于 `src/main/resources/mapper`。
  - 统一使用 `CAST(... AS uuid/jsonb)` 与 `TEXT[]` TypeHandler 等约定。
- **异步与调度**：
  - `AsyncConfig` 开启 `@EnableAsync` 与 `@EnableScheduling`，提供通用 `Executor`。
  - Memory Hub 与 MCP 埋点均复用异步执行线程池。

### 3. 关键设计原则与权衡

1. **工具调用可控性**：关闭 Spring AI 内建工具自动执行（`internalToolExecutionEnabled=false`），由本地 `JChatMind` 明确执行工具并统一持久化日志与 SSE 推送。
2. **记忆系统渐进集成**：Memory Hub 默认关闭，启用时优先作为上下文来源，无数据时回退旧逻辑，避免一次性迁移带来的风险。
3. **MCP 轻量接入**：
   - 复用 Spring AI 官方 MCP 客户端与 mcp-proxy-server 做传输/发现。
   - 项目层不再维护 MCP Server/Tool 注册表，只持久化调用记录，降低耦合复杂度。
4. **异步与稳健性**：
   - 记忆整理任务与 MCP 调用埋点均采用异步执行，失败仅记日志，不影响主业务链路。
5. **可评估性**：
   - 针对 Memory Hub 单独构建了离线评测 harness 与数据集，支持在不影响生产逻辑的前提下对召回与摘要质量做量化评估。

### 4. 典型请求生命周期（综述）

1. 用户在前端发起问题，经 Controller 持久化到 `chat_message`，并通过 SSE 通知。
2. 后端调用 `JChatMindFactory.create(agentId, sessionId)`：
   - 加载 Agent 配置与知识库。
   - 按 Memory Hub 开关与会话 ID 构建记忆上下文。
   - 基于 `allowedTools` 组装本地 Tool 与 MCP 工具回调集合。
3. 创建 `JChatMind` 并执行 `.run()`：
   - 多轮 Think–Execute：
     - `think()`：模型基于上下文与工具列表决策是否调用工具。
     - `execute()`：使用 `ToolCallingManager` 调用本地工具与 MCP 工具，得到 `ToolResponseMessage`。
     - 将带 `tool_calls` 的 `AssistantMessage` 与 `ToolResponseMessage` 以正确顺序持久化与推送。
4. 过程中所有重要对话片段会被 Memory Hub 镜像写入分层记忆，并在会话结束或阈值触发时由 `MemoryAgent` 整理；所有 MCP 工具调用会被 `RecordingToolCallback` 异步写入 `t_mcp_tool_call`，供后续分析与监控使用。

