# JChatMindv2 — AI 编程 Agent 平台

> 基于 Scheduler-Worker-Reviewer 三层编排的智能编码辅助系统。

## 架构概览

用户请求 -> Scheduler(拆解DAG) -> Worker(并行编码) -> Reviewer(审查) -> 完成

### 核心能力

| 模块 | 说明 |
|------|------|
| AI Coding | Scheduler 拆解需求 -> 多 Worker 并行执行 -> Reviewer 自动审查循环 |
| 普通对话 | 选择任意 Agent 进行智能对话，知识库集成 RAG 问答 |
| 知识库 | 向量化文档管理，Agent 按需检索 |
| Session 管理 | thread.jsonl / notes.md / meta.json 三层文件持久化 |

### 技术栈

- 后端: Java 17 + Spring Boot 3.5 + Spring AI 1.1 + PostgreSQL + MyBatis
- 前端: React + TypeScript + Vite + Ant Design X
- LLM: DeepSeek / 智谱 GLM
- MCP: mcp-proxy + shell 命令执行
- 记忆系统: Ollama(bge-m3) + PgVector RAG 分层记忆

## 项目结构

```
JChatMindv2/
  JChatMind/
    jchatmind/          # Spring Boot 后端
    ui/                 # React 前端
    scripts/mcp/        # MCP 服务器 (Node.js)
  graphify-out/         # 代码知识图谱
  agent-profiles/       # Agent 角色 YAML 配置
  workspace/            # 工作区
  .jchatmind/sessions/  # 会话文件存储
```