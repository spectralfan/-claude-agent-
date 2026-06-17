# JChatMindv2 — AI 编程 Agent 平台

> Agent 自主 ReAct（Reason→Act→Observe）循环的智能编码辅助系统。

## 架构概览

用户请求 → JChatMind ReAct Loop → `spawn_agent` 委派(planner/worker/reviewer) → BashTool 执行 → 任务追踪 → 完成

### 核心能力

| 模块 | 说明 |
|------|------|
| AI Coding | ReAct Agent Loop：推理→工具调用→结果观察循环，Agent 自主决策工具和子 Agent 委派 |
| 普通对话 | 选择自定义 Agent 进行智能对话，知识库集成 RAG 问答 |
| 知识库 | 向量化文档管理(Ollama bge-m3 + PgVector)，Agent 按需检索 |
| Session 管理 | thread.jsonl / notes.md / meta.json 多层文件持久化，CHAT/CODING 类型分离 |
| 事件回放 | 每次 run 自动持久化 events.jsonl，支持断线重连回放 |

### 技术栈

- 后端: Java 17 + Spring Boot 3.5 + Spring AI 1.1 + PostgreSQL 16 + MyBatis + RocketMQ
- 前端: React 19 + TypeScript + Vite + Ant Design X
- LLM: DeepSeek / 智谱 GLM
- 工具执行: 内置 BashTool（ProcessBuilder 直连，自动注入工作目录）
- Agent 编排: `spawn_agent` 委派 planner/worker/reviewer 子 Agent，轻量任务系统(task_create/update/list/get)
- 权限体系: 6 层策略评估 + 异步审批 + 前端弹窗（write/execute_command 需审批，read/task 默认放行）
- 记忆系统: 分层记忆 + PgVector 向量检索

## 快速开始

```bash
# 1. 启动 PostgreSQL
docker run -d --name postgres -e POSTGRES_PASSWORD=123456 -p 5432:5432 postgres:16

# 2. 初始化数据库（14 张表一次性建完）
psql -U postgres -d jchatmind -f databasesql/jchatmind_full_schema.sql

# 3. 配置 API Key
export DEEPSEEK_API_KEY=sk-xxxx

# 4. 启动后端
cd JChatMind/jchatmind
./mvnw spring-boot:run

# 5. 启动前端
cd JChatMind/ui
npm install && npm run dev
```

## 项目结构

```
JChatMindv2/
  JChatMind/
    jchatmind/                # Spring Boot 后端 (Java 17)
      src/main/java/          # 源码
        agent/                # Agent ReAct 循环、Factory、Profile、Tool 注册
        coding/               # 工作区管理、任务系统、BashTool、权限桥接
        mcp/                  # MCP 集成、权限管理 (6层评估)、工具埋点
        session/              # Session 管理、EventBus、事件持久化、AgentLoop
        event/                # 事件监听、ReplayController (回放API)
        rpc/                  # WebSocket JSON-RPC、RpcEventBridge
    ui/                       # React 前端
  databasesql/                # 数据库完整 DDL（一次性还原）
  graphify-out/               # 代码知识图谱
  .jchatmind/                 # 会话文件存储
```