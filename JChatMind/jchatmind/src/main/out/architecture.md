# JChatMindv2 项目架构文档

> 基于实际代码扫描生成 · 2026-06-15

---

## 1. 项目概述

JChatMindv2 是一个 AI 编程 Agent 平台，采用 KamaClaude 风格的 plan→act→observe 循环。
后端 Java 17 + Spring Boot 3 + Spring AI 1.1 + PostgreSQL 16 + MyBatis。
前端 React + TypeScript + Vite + Ant Design X。
MCP 通过 STDIO 直连 Git Bash 执行外壳命令。

---

## 2. 核心模块

### 2.1 Agent 系统 (`agent/`)

| 类 | 职责 |
|----|------|
| `JChatMind` | Agent 运行时：reason→act→observe 循环，最大 step 限制 |
| `JChatMindFactory` | Agent 工厂：组装 systemPrompt、工具、记忆、Profile 子 Agent |
| `AgentState` | Agent 状态枚举（RUNNING / FINISHED / ERROR） |
| `AgentToolResultProperties` | 工具结果压缩配置 |

**Tool 注册（`agent/tools/`）：**

| 工具 | 类 | 说明 |
|------|-----|------|
| `spawn_agent` / `agent_result` | `SpawnAgentTool` | 子 Agent 委派（前台阻塞/后台并行），嵌套限制 2 层（子可再 spawn） |
| `task_create` / `task_update` / `task_list` / `task_get` | `TaskCreateTool` 等 | 轻量任务追踪（pending→in_progress→completed），blocked_by 依赖管理 |
| `execute_command` | `BashTool` | 内置 shell 执行（ProcessBuilder 直连，自动 workspace cwd） |
| `write_coding_file` / `append_coding_file` | `WriteFileTool` | 写入/追加文件（工作区边界检查） |
| `read_coding_file` | `ReadFileTool` | 读取文件 |
| `read_coding_files` | `BatchReadTool` | 批量读取 |
| `list_coding_directory_tree` / `list_coding_directory` | `ListDirTool` | 目录列出 |
| `direct_answer` | `DirectAnswerTool` | 直接回答用户 |
| `terminate` | `TerminateTool` | 结束任务 |
| `save_note` | `NoteSaveTool` | 保存笔记 |
| `databaseQuery` | `DataBaseTools` | 数据库只读查询 |
| `knowledge_search` | `KnowledgeTools` | 知识库检索 |
| `ToolRegistry` | — | 统一工具注册，按 Agent Profile / Session 过滤 |

**Agent Profile（`agent/profile/`）：**
- `AgentProfile` — YAML 驱动的角色定义（name、systemPrompt、allowedTools、maxSteps）
- `AgentProfileLoader` — 启动时从 `agent-profiles/*.yaml` 加载
- `AgentProfileService` — 运行时查询 Profile

**配置目录 `src/main/resources/agent-profiles/`：**
- `planner.yaml` — 规划者（只读）
- `worker.yaml` — 执行者（读写+执行）
- `reviewer.yaml` — 审查者（只读）

### 2.2 MCP 集成 (`mcp/`)

| 类 | 职责 |
|----|------|
| `McpClientManager` | STDIO 直连 MCP 客户端管理，启动时发现工具 |
| `McpIntegrationImpl` | MCP 工具注入 Agent，白名单过滤 |
| `McpToolBridgeImpl` | MCP 工具回调桥接 |
| `RecordingToolCallback` | 工具调用包装：记录埋点 + 权限审批拦截 |
| `McpCallRecorder` | MCP 调用异步记录到 `t_mcp_tool_call` 表 |
| `McpShellCommandPolicy` | 外壳命令安全策略（硬拒 `rm -rf`/`shutdown` 等） |

**权限系统（`mcp/permission/`）：**

| 类 | 职责 |
|----|------|
| `PermissionManager` | 6 层策略评估（deny→outside_cwd→session cache→persistent→allow→default），Future 异步审批，session+persistent 两级缓存 |
| `PermissionAwareToolCallback` | 非 MCP 自定义工具权限包装器 |
| `ToolPolicy` | 每个工具的默认策略 + allow/deny 正则列表 |
| `PermissionController` | REST API：`GET/PUT /api/mcp/permission/mode`、`POST /api/mcp/permission/respond` |

**BashTool（`agent/tools/coding/BashTool.java`）：**
- 内置 ProcessBuilder 直连执行，无需 MCP shell proxy
- 自动注入 coding workspace 为工作目录
- 64KB 输出截断，120s 超时，高危命令拦截

**MCP Shell Server（`scripts/mcp/jchatmind-shell-mcp.mjs`，保留作降级）：**
- 纯 bash 执行，无 PowerShell 依赖
- 反斜杠路径自动转正斜杠

### 2.3 事件系统 (`session/event/` + `rpc/`)

| 类 | 职责 |
|----|------|
| `EventBus` | 内存事件总线（pub/sub） |
| `RpcEventBridge` | EventBus → WebSocket 桥接，订阅所有事件推送到前端 |
| `ChatWebSocketHandler` | WebSocket 连接管理 |
| `JsonRpcDispatcher` | JSON-RPC 消息路由 |

**事件类型（全部通过 WebSocket 推送）：**

| 事件 | 说明 |
|------|------|
| `run.started` / `run.finished` | Agent 运行生命周期 |
| `step.started` / `step.finished` | 每步循环 |
| `tool.called` / `tool.result` | 工具调用 |
| `subagent.started` / `subagent.finished` | 子 Agent 生命周期 |
| `permission.requested` | 工具审批请求 |
| `llm.usage` | LLM token 用量 |
| `context.compacted` | 上下文压缩 |

### 2.4 Coding 系统 (`coding/`)

| 模块 | 职责 |
|------|------|
| `CodingTaskService` | 编码任务 CRUD，工作区路径管理 |
| `CodingTaskAutoProvisionerImpl` | 会话启动时自动创建 Coding 任务 |
| `CodingWorkspaceServiceImpl` | 工作区根路径管理、边界安全检查 |
| `CodingPromptComposerImpl` | 系统 Prompt 组装（仅注入 task 上下文，不加工作流指令） |
| `CodingSessionContext` | ThreadLocal 绑定的 Session/Agent 上下文 |
| `SubAgentRunContext` | 子 Agent 运行边界标记（深度控制：子可再 spawn 孙） |
| `AgentPresetBootstrapService` | 启动时确保 AI Coding 预设 Agent 存在 |
| `CodingStackServiceImpl` | 技术栈配置加载（`coding-stacks/*.json`） |
| `CodingSkillServiceImpl` | 自主开发技能加载（`coding-skills/*.json`） |
| `AgentTask` / `TaskManager` | 文件系统轻量任务管理（`.tasks/task_*.json`），Agent 自主拆解追踪 |

### 2.5 Session 管理 (`session/`)

| 组件 | 说明 |
|------|------|
| `SessionManagerImpl` | 会话创建（CHAT/CODING 类型） |
| `ThreadStore` | thread.jsonl 文件存储（持久化对话） |
| `NoteStore` | notes.md 文件存储 |
| `MetaStore` | meta.json 文件存储 |
| `AgentLoop` | 独立 Agent 循环（子 Agent 用） |
| `SubAgentExecutor` | 子 Agent 异步执行器 |
| `ContextCompactor` | 上下文自动压缩（token 超阈值时触发） |

### 2.6 记忆系统 (`memory/`)

| 模块 | 说明 |
|------|------|
| `MemoryAgent` | 记忆整理 Agent（会话结束时触发） |
| `MemoryIntegrationImpl` | 记忆注入 Agent 上下文 |
| `MemoryServiceImpl` | 记忆 CRUD |
| `RecentMemoryServiceImpl` | 近期记忆管理 |
| `ArchiveMemoryServiceImpl` | 归档记忆管理 |
| `WorkingMemoryServiceImpl` | 工作记忆管理 |

### 2.7 前端 (`ui/`)

| 组件 | 说明 |
|------|------|
| `CodingView` | AI Coding 主页面 |
| `AgentChatView` | Agent 对话页面 |
| `AgentChatHistory` | 对话历史渲染（含子 Agent 进度卡片） |
| `CodingChatInput` | 输入框（@文件引用） |
| `CodingFileTree` | 工作区文件树 |
| `CodingFilePreview` | 文件预览/差异显示 |
| `PermissionDialog` | 工具调用审批弹窗 |
| `event-bridge.ts` | WebSocket 事件桥接 |
| `useSessionSse.ts` | WebSocket 事件订阅 Hook |

---

## 3. 数据流

```
用户输入 → POST /chat-messages → ChatEventListener(异步)
  → JChatMindFactory.create() ─ 组装 systemPrompt + 工具 + 记忆
  → JChatMind.run() ─ plan→act→observe 循环
    → LLM.chat() ─ 返回 tool_calls
    → RecordingToolCallback.invoke()
      → PermissionManager.requestApproval() ─ ask 模式弹窗 / auto 放行
      → delegate.call() ─ 执行工具
    → EventBus.publish(ToolResultEvent) → RpcEventBridge → WebSocket → 前端
  → SessionManager 保存消息
  → MemoryAgent 触发记忆整理
```

---

## 4. 关键设计决策

1. **纯 bash MCP** — 放弃 PowerShell，所有命令通过 Git Bash 执行
2. **Profile 驱动 Agent** — planner/worker/reviewer 由 YAML 配置，KamaClaude 风格
3. **权限审批** — ask/auto 双模式 + 三层策略，ask 模式通过 WebSocket 弹窗用户确认
4. **无强制工作流** — Agent 自主决策工具使用，不强制 planner→executor→reviewer 流程
5. **STDIO 直连 MCP** — 删除 SSE Proxy，MCP 子进程直接与 Spring AI 通信
6. **WebSocket 事件流** — 所有 Agent 状态变化实时推送到前端