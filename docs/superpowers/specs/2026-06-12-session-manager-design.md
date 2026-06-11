# Session 管理层设计文档

> 为 JChatMindv2 引入 Thread.jsonl / Notes.md / SessionManager 三层会话体系

## 动机

当前 JChatMindv2 的会话管理存在几个瓶颈：

1. **消息存 DB 表** — 无法回放、无法压缩、无法追溯 run 级别历史
2. **无 run 概念** — 一次 Agent 执行（含多步 Loop）没有独立的 trace 单元
3. **无 Agent 事实记忆** — Worker 之间、Run 之间无法共享持久化的事实
4. **会话状态分散** — CodingSessionContext、SubAgentRunContext、ChatSessionProvisioner、OrchestratorContinuationService 各自管理一部分状态，没有统一协调者
5. **无上下文治理** — 长会话无水位检测，无 compact 机制

## 设计原则

- **文件为主，DB 为索引** — thread.jsonl 是消息的唯一权威源
- **Run 单元化** — 一次 Agent 执行 = 一个 run，run_id 贯穿所有存储
- **渐进式切换** — Phase by phase，不破坏现有功能
- **兼容现有 API** — 前端无感知切换

## 总体架构

`
.jchatmind/sessions/<sessionId>/
├── meta.json              # 会话元数据（状态、创建时间、agentId 等）
├── thread.jsonl           # 完整消息流（追加写，JSONL 格式）
├── thread_<ts>.jsonl.bak  # compact 前的备份
├── notes.md               # Agent 主动记录的事实
└── runs/
    └── <runId>/
        ├── events.jsonl   # 该 run 的事件流
        └── ...
`

## 三个核心抽象

### 1. ThreadStore

**职责**：thread.jsonl 的读写维护

- appendMessage(sid, role, content, runId) — 追加一条消息
- appendMessages(sid, messages, runId) — 批量追加
- readMessages(sid) — 读取完整 thread，返回 Anthropic API 兼容格式
- writeCompacted(sid, messages) — 压缩后覆盖写入 + 旧文件备份
- trimOrphanToolUse(messages) — 裁掉尾部未配对 tool_use

### 2. NoteStore

**职责**：notes.md 的读写维护

- readNotes(sid) — 读取全文
- appendNote(sid, content, runId) — 追加一条事实记录

### 3. SessionManager

**职责**：统一会话生命周期

- **状态管理**：ACTIVE / PAUSED / COMPLETED / FAILED
- **并发控制**：数据库乐观锁（version 字段 on chat_session）
- **事件发布**：SessionCreatedEvent / RunStartedEvent / RunFinishedEvent
- **存储协调**：统一管理 ThreadStore + NoteStore + MetaStore

## 实施路径

| Phase | 内容 | 核心文件 |
|-------|------|----------|
| 1 | ThreadStore 核心类 + 测试 | ThreadStore.java, ThreadStoreTest.java |
| 2 | NoteStore + NoteSaveTool + 测试 | NoteStore.java, NoteSaveTool.java, 测试 |
| 3 | SessionManager + 乐观锁 + 事件 | SessionManager.java, SessionManagerImpl.java, 事件类 |
| 4 | 消息 API 切换（Controller 从 thread.jsonl 读） | ChatMessageFacadeServiceImpl 改写 |
