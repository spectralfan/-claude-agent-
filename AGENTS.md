# JChatMindv2 — AI 编程 Agent 平台


## 强制开发规范（每次会话自动生效）

本项目的开发必须遵循以下宪章规则。**所有代码变更前必须按此流程执行。**

### 变更类型判断

进入工作前先判断变更类型：

| 类型 | 标准 | 流程 |
|------|------|------|
| **A: 大功能/新特性** | 新增文件 > 3 个或涉及新模块 | 完整 OpenSpec + Superpowers 流程 |
| **B: 小修改/Bug修复** | 修改文件 ≤ 3 个 | Superpowers 简化版 |
| **C: 单行/文档级** | 仅文档/配置/单行代码 | 直接修改 + 验证 + 提交 |

### 类型 A 必须执行的三段流程

**1. OpenSpec 阶段：** 规格管理
- 初始化：openspec init（仅首次）
- 启动变更：/opsx:new <name> → 生成 proposal / specs / design / tasks
- 快速生成：/opsx:ff <name>

**2. Superpowers 执行阶段：**
- brainstorming → 设计文档到 docs/superpowers/specs/
- writing-plans → 计划到 docs/superpowers/plans/
- TDD 红-绿-重构循环 → 测试代码 + 实现代码
- verification-before-completion → 逐项确认
- requesting-code-review → 代码审查

**3. OpenSpec 收尾：**
- /opsx:verify → 验证报告
- /opsx:archive → 归档变更

### TDD 铁律
- 先写测试再写实现
- 红（失败测试）→ 绿（最少代码通过）→ 重构
- 未经测试的代码不存在

### 红线（禁止行为）
| 禁止 | 正确做法 |
|------|----------|
| "这只是简单问题，不需要技能" | 先检查技能再回应 |
| "我先查查代码库" | 先用技能 |
| "不需要测试" | TDD 铁律 |
| "先实现再完善" | 规格先行，设计先行 |

---

## Project

| 属性 | 值 |
|------|-----|
| 后端 | Java 17 + Spring Boot 3.5.8 + Spring AI 1.1 + PostgreSQL 16 + MyBatis |
| 前端 | React + TypeScript + Vite + Ant Design X |
| LLM | DeepSeek / 智谱 GLM |
| MCP | mcp-proxy SSE + Node.js shell-mcp |
| 入口 | JchatmindApplication.java / main.tsx |

## Commands

```bash
# 后端
cd JChatMind/jchatmind && ./mvnw spring-boot:run  # :8080
cd JChatMind/jchatmind && ./mvnw compile           # 编译
cd JChatMind/jchatmind && ./mvnw test -Dtest="ClassName"  # 测试

# 前端
cd JChatMind/ui && npm run dev     # 开发
cd JChatMind/ui && npm run build   # 构建

# 外部依赖
docker run -d --name postgres -e POSTGRES_PASSWORD=123456 -p 5432:5432 postgres:16
pwsh scripts/e2e/coding-tank-game-e2e.ps1  # E2E
```

## Architecture

> 详细架构见 `JChatMind/jchatmind/src/main/out/architecture.md` · Graphify 报告见 `graphify-out/GRAPH_REPORT.md`

| 模块 | 包路径 | 职责 |
|------|--------|------|
| Agent | `agent/` | JChatMind ReAct 运行时, Factory 组装, Profile YAML 角色定义, BashTool 内置执行、任务工具（task_create/update/list/get）、子 Agent 委派等 |
| Coding | `coding/` | 编码任务管理, 工作区隔离, Prompt 组装, 轻量任务系统（.tasks/ 文件存储）, 技术栈配置, 审批模式, SubAgentExecutor 子代理引擎 |
| Session | `session/` | SessionManager（CHAT/CODING）, ThreadStore/NoteStore/MetaStore, EventBus 内存总线, AgentLoop 独立循环 |
| Event | `session/event/` + `rpc/` | WebSocket + EventBus 事件流, EventWriter 持久化 events.jsonl, ReplayController 断线回放, RpcEventBridge 推送 |
| MCP | `mcp/` | STDIO 直连 MCP 客户端, RecordingToolCallback 埋点, PermissionManager（6 层策略评估 + Future 异步审批 + 两级缓存）, PermissionAwareToolCallback 自定义工具权限包装 |
| Memory | `memory/` | RAG 检索（Ollama bge-m3 + PgVector）, MemoryAgent 会话结束整理, 三段记忆（工作/近期/归档） |
| UI | `ui/` | CodingView 编码主界面, AgentChatHistory（含子 Agent 进度）, PermissionDialog 审批弹窗, WebSocket 事件桥接 |

### 核心抽象（Top 10 God Nodes · Graphify）
`JChatMind`(42 edges) · `JChatMindFactory`(36) · `CodingWorkspaceService`(35) · `MemoryService`(22) · `ExecutionContext`(22) · `SessionMeta`(22) · `AgentProfile` · `EventBus` · `PermissionManager` · `AgentLoop`

### 关键设计决策
1. **内置 BashTool** — ProcessBuilder 直连，自动识别 coding workspace 工作目录
2. **Profile 驱动 Agent** — planner/worker/reviewer 由 `agent-profiles/*.yaml` 配置
3. **无强制工作流** — Agent 自主决策工具使用，配合轻量任务系统（task_*）追踪子目标进度
6. **权限审批** — 修改操作用具弹窗审批（ask/auto 双模式），只读/编排类默认放行
4. **STDIO 直连 MCP** — 删除 SSE Proxy，子进程直连 Spring AI
5. **WebSocket 事件流 + 持久化回放** — Agent 执行过程实时推送 + events.jsonl 持久化 + 断线事件重放

> **注意**：每次涉及模块增删或架构调整的开发任务后，需同步更新 `JChatMind/jchatmind/src/main/out/architecture.md` 和本文 Architecture 部分，并运行 `graphify update .` 刷新图谱。

## Conventions

- **TDD**: 先写测试再实现，红-绿-重构
- **中文**: 文档/commit/注释用中文，代码标识符用英文
- **设计先行**: 大功能先出 docs/superpowers/specs/
- **Agent Profile**: 角色定义在  gent-profiles/*.yaml
- **Session 类型**: 按 Agent 工具自动检测 CHAT/CODING
- **Shell 执行**: 规范名 bash
- **Graphify + Codegraph**: 架构查询双引擎

## Notes