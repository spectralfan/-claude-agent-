# 执行计划：Session 管理层重构

## 已完成

### Phase 1 — ThreadStore
- [x] ThreadStore.java (核心类：appendMessage/appendMessages/readMessages/writeCompacted/deleteThread/trimOrphanToolUse)
- [x] SessionProperties.java (配置：session.store-root)
- [x] ThreadStoreTest.java (9 个测试)
- [x] application.yaml 添加 session 配置

### Phase 2 — NoteStore + NoteSaveTool
- [x] NoteStore.java (readNotes/appendNote/deleteNotes)
- [x] NoteSaveTool.java (Agent 可调用工具，ToolType.OPTIONAL)
- [x] SessionRunIdGenerator.java (run_<uuid> 生成器)
- [x] NoteStoreTest.java (4 个测试) + NoteSaveToolTest.java (4 个测试)

### Phase 3 — SessionManager
- [x] SessionState.java (CREATED/ACTIVE/PAUSED/COMPLETED/FAILED)
- [x] SessionMeta.java (持久化到 meta.json)
- [x] SessionEvent.java (Created/RunStarted/RunFinished/StateChanged)
- [x] MetaStore.java (meta.json 读写)
- [x] SessionManager.java (接口定义)
- [x] SessionManagerImpl.java (Spring @Service 实现)
- [x] MetaStoreTest.java (3 个测试)

### Phase 4 — 消息 API 切换
- [x] ChatMessageFacadeServiceImpl: getChatMessagesBySessionId() 从 thread.jsonl 读取
- [x] doCreateChatMessage() 双写 DB + thread.jsonl
- [x] DB 降级为索引层

## 文件清单

### 新增 (12 个)
| 文件 | 包 | 说明 |
|------|-----|------|
| ThreadStore.java | session.store | thread.jsonl 读写 |
| NoteStore.java | session.store | notes.md 读写 |
| MetaStore.java | session.store | meta.json 读写 |
| SessionProperties.java | session.config | 配置类 |
| SessionState.java | session | 状态枚举 |
| SessionMeta.java | session | 元数据模型 |
| SessionEvent.java | session | 事件体系 |
| SessionRunIdGenerator.java | session | Run ID 生成器 |
| SessionManager.java | session | 接口 |
| SessionManagerImpl.java | session.impl | 实现 |
| NoteSaveTool.java | agent.tools.session | Agent 笔记工具 |
| ThreadStoreTest.java | (test) | 9 测试 |
| NoteStoreTest.java | (test) | 4 测试 |
| NoteSaveToolTest.java | (test) | 4 测试 |
| MetaStoreTest.java | (test) | 3 测试 |

### 修改 (2 个)
| 文件 | 变更 |
|------|------|
| application.yaml | 添加 session.store-root 配置 |
| ChatMessageFacadeServiceImpl.java | 集成 SessionManager，双写 + 优先从 thread.jsonl 读 |

## 架构图

```
                    ┌──────────────────┐
                    │  ChatMessageVO   │ (前端)
                    └────────┬─────────┘
                             │ GET /api/chat-messages/session/{id}
                    ┌────────▼─────────┐
                    │ ChatMessage      │
                    │ FacadeServiceImpl│
                    └──┬──────────┬────┘
                       │          │
              ┌────────▼──┐  ┌────▼──────────┐
              │ thread.   │  │ DB            │
              │ jsonl     │  │ (chat_message)│
              │ (权威源)   │  │ (索引/回退)    │
              └─────┬─────┘  └───────────────┘
                    │
          ┌─────────▼──────────┐
          │   SessionManager   │
          │                    │
          │  ┌─ ThreadStore    │
          │  ├─ NoteStore      │
          │  ├─ MetaStore      │
          │  └─ SessionEvent   │
          └─────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │ NoteSaveTool        │
         │ (save_note 工具)     │
         └─────────────────────┘
```