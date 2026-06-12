# JChatMind — AI Coding Agent 平台

> JChatMindv2 的子项目，包含 Spring Boot 后端和 React 前端。

## 模块

| 目录 | 说明 |
|------|------|
| `jchatmind/` | Spring Boot 3.5 后端，Agent 编排、Session 管理、MCP 集成 |
| `ui/` | React 19 + TypeScript 前端，支持普通对话 / AI Coding / 知识库 |

## 后端核心模块

| 包 | 职责 |
|----|------|
| `agent/` | JChatMind Agent 系统，Profile YAML 配置，Tool 注册 |
| `coding/` | Scheduler-Worker-Reviewer 编排引擎，DAG 调度 |
| `session/` | SessionManager，ThreadStore，NoteStore，EventBus，AgentLoop |
| `mcp/` | MCP 协议桥接，McpClientManager 工具发现 |
| `memory/` | 分层记忆系统，RAG 向量检索 |

## 启动

```bash
# 后端
cd jchatmind && ./mvnw spring-boot:run     # :8080

# 前端
cd ui && npm run dev                        # :5173
```