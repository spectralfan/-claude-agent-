# JChatMindv2 — AI 编程 Agent 平台

> 基于 Scheduler-Worker-Reviewer 三层编排的智能编码辅助系统。

## 架构概览

用户请求 -> Scheduler(拆解DAG) -> Worker(并行编码) -> Reviewer(审查) -> 完成

### 核心能力

| 模块 | 说明 |
|------|------|
| AI Coding | Scheduler 拆解需求 -> 多 Worker 并行执行 -> Reviewer 自动审查循环，支持 Continuation 自动继续 |
| 普通对话 | 选择自定义 Agent 进行智能对话，知识库集成 RAG 问答 |
| 知识库 | 向量化文档管理(Ollama bge-m3 + PgVector)，Agent 按需检索 |
| Session 管理 | thread.jsonl / notes.md / meta.json 三层文件持久化，CHAT/CODING 类型分离 |

### 技术栈

- 后端: Java 17 + Spring Boot 3.5 + Spring AI 1.1 + PostgreSQL 16 + MyBatis + RocketMQ
- 前端: React 19 + TypeScript + Vite + Ant Design X
- LLM: DeepSeek / 智谱 GLM
- MCP: STDIO 直连子进程 (jchatmind-shell-mcp)
- 编排引擎: Scheduler-Worker-Reviewer DAG 调度 + EventBus + AgentLoop

## 项目结构

```
JChatMindv2/
  JChatMind/
    jchatmind/          # Spring Boot 后端
    ui/                 # React 前端
  scripts/mcp/          # MCP 服务器 (Node.js)
  docs/                 # 设计文档和架构说明
  graphify-out/         # 代码知识图谱
  agent-profiles/       # Agent 角色 YAML 配置
  workspace/            # 工作区产物
  .jchatmind/sessions/  # 会话文件存储 (thread.jsonl)
```

## 快速开始

```bash
# 1. 启动 PostgreSQL (Docker)
docker run -d --name postgres -e POSTGRES_PASSWORD=123456 -p 5432:5432 postgres:16

# 2. 配置 API Key
export DEEPSEEK_API_KEY=sk-xxxx

# 3. 启动后端
cd JChatMind/jchatmind
./mvnw spring-boot:run

# 4. 启动前端
cd JChatMind/ui
npm install && npm run dev
```