# JSON-RPC 2.0 over WebSocket 改造设计

> 将现有 REST + SSE 双通道通信改为单一 WebSocket 连接，协议采用 JSON-RPC 2.0。

## 现状

发消息: fetch POST /api/chat-messages (REST)
收事件: EventSource /sse/connect/{sessionId} (SSE)

## 目标

一条 WebSocket 连接，按 JSON-RPC 2.0 规范通信。

```

Client → Server: {"jsonrpc":"2.0","id":1,"method":"chat.send","params":{...}}
Server → Client: {"jsonrpc":"2.0","id":1,"result":{"messageId":"xxx"}}

Server → Client: {"jsonrpc":"2.0","method":"event.run.started","params":{...}}  (notification)
```

## 新增文件

| 文件 | 说明 |
|------|------|
| JsonRpcMessage.java | 消息模型 (request/response/notification) |
| JsonRpcDispatcher.java | 按 method 分发到对应 Service |
| ChatWebSocketHandler.java | WebSocket 端点 + 会话管理 |
| WebSocketConfig.java | Spring WebSocket 配置 |
| frontend/ws/rpc-client.ts | 前端 RPC 客户端 |
| frontend/ws/EventBridge.ts | 事件桥接 (WebSocket ↔ React) |

## RPC 方法清单

### 请求方法 (Request → Response)
| method | params | 响应 result |
|--------|--------|------------|
| chat.send | {sessionId, agentId, role, content} | {messageId} |
| session.create | {agentId, title} | {sessionId} |
| session.list | {type?} | [ChatSessionVO] |
| task.create | {sessionId, ...} | {taskId} |
| task.list | {sessionId} | [OrchestrationTaskDTO] |

### 通知方法 (Server → Client, JSON-RPC Notification)
| method | params | 来源 |
|--------|--------|------|
| event.run.started | {runId, goal, ts} | EventBus |
| event.run.finished | {runId, status, steps} | EventBus |
| event.step.started | {runId, step} | EventBus |
| event.tool.called | {runId, tool, args} | EventBus |
| event.llm.usage | {runId, inputTokens, outputTokens} | EventBus |
| coding.subtask.started | {taskId, title, role} | OrchestrationTaskDispatcher |
| coding.subtask.completed | {taskId, status} | OrchestrationTaskDispatcher |
| sse.* | (兼容现有 SSE 事件) | 现有 SseEmitter |

### 错误响应
```json
{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"会话不存在"}}
```