# AI 智能体助手 — JChatMind

基于 [代码随想录](https://programmercarl.com) 卡哥 JChatMind 项目做的优化与扩展，目标是实现 **Claude Code 级别的 AI Coding Agent**：多 Agent 协作、MCP 终端执行、栈感知验证与产品全流程自动交付。

**在线仓库**：[spectralfan/-claude-agent-](https://github.com/spectralfan/-claude-agent-)

---

## 项目演进

### 开始状态

- 单 Agent
- Spring AI 自带工具调用
- Think → Execute 循环架构
- 记忆：简单滑动窗口；部分 RAG 记忆存入 PostgreSQL 向量库
- 消息经 SSE 推送到前端

### 更新 1

- 循环范式改为 **ReAct**（推理 → 工具调用 → 观察 → 继续推理）
- 记忆系统拆分为短期 / 长期（Memory Hub，可选启用）
- 增加 **AI Coding** 工作台与 **MCP** 外部工具接入

### 更新 2（当前）

- **多 Agent 协作**：Orchestrator 编排 + Worker 子 Agent 异步执行
- **消息窗口修复**：`ToolAwareMessageWindowChatMemory` 按 tool 轮次裁剪，Coding 场景 `memory-window: 80`
- **仿 Claude Code 全流程**：从自然语言需求到改代码、终端验证、`mark_coding_complete` 交付
- **Python 依赖管理**：脚手架与 Agent 引导统一使用 **uv**（`uv sync` / `uv run pytest`），MCP 执行终端命令
- **Windows 友好 MCP Shell**：自建 `jchatmind-shell-mcp.mjs`，经 `mcp-proxy` SSE 桥接，替代不兼容的 bash 类 server
- **敏感配置外置**：DeepSeek / 智谱 API Key 从环境变量读取（`OPEN_AI_API_KEY`、`ZHIPU_API_KEY`）

---

## 当前能力一览

| 能力 | 说明 | 默认 |
|------|------|------|
| 多 Agent 对话 | 可配置模型、系统提示词、工具白名单 | 开启 |
| ReAct 工具闭环 | `reason → act → observe`，手动 `ToolCallingManager` | 开启 |
| 知识库 RAG | 文档分块、向量检索（pgvector） | 开启 |
| Memory Hub | WORKING / RECENT / ARCHIVE 分层记忆 | 开启 |
| MCP 工具 | `mcp-proxy` + Spring AI `McpSyncClient`，按白名单注入 Agent | 可配置 |
| AI Coding | 文件树、Diff、终端输出 SSE、栈 Profile、Skill 注入 | 开启 |
| Orchestrator / Worker | 编排委派子任务，Worker 独立会话执行 | 开启 |
| 交付验证 | `mark_coding_complete` 前可要求验证命令 exit 0 | 可配置 |

---

## 仓库结构

```
JChatMindv2/
├── JChatMind/
│   ├── jchatmind/          # Spring Boot 后端 (:8080)
│   │   ├── src/main/java/
│   │   ├── src/main/resources/
│   │   │   ├── application.yaml
│   │   │   ├── coding-agent-preset.json
│   │   │   └── coding-templates/    # Python/Java 脚手架
│   │   ├── scripts/mcp/             # MCP shell + proxy 启动脚本
│   │   └── workspace/               # Coding 工作区（E2E 样例）
│   └── ui/                 # React + Vite 前端 (:5173)
└── jchatmind_v2/           # SQL 脚本等资源
```

详细架构见：`jchatmind/src/main/out/architecture.md`  
联调指南见：`jchatmind/src/main/out/Coding_Setup.md`

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.5.8、Spring AI 1.1.0、MyBatis、PostgreSQL |
| 前端 | React 19、Vite、Ant Design、Tailwind |
| LLM | DeepSeek、智谱 GLM-4 |
| 工具 | MCP（SSE）、本地 Java 工具（文件/Maven/Coding 交付） |
| Python | uv + pytest 脚手架 |

---

## Memory Hub（分层记忆）

已启用 `memory.hub.enabled: true`。

| 层级 | 说明 |
|------|------|
| WORKING | 短期，镜像 `chat_message` |
| RECENT | 中期，高重要性记忆 |
| ARCHIVE | 长期，摘要 + pgvector 向量检索 |

**首次部署**（PostgreSQL on Docker，容器名 `postgres`）：

```powershell
# 建表（若尚未执行）
docker cp JChatMind/jchatmind/src/main/resources/db/memory_hub.sql postgres:/tmp/
docker exec postgres psql -U postgres -d jchatmind -f /tmp/memory_hub.sql

# 可选：迁移历史 chat_message
docker cp JChatMind/jchatmind/src/main/resources/db/memory_hub_migration.sql postgres:/tmp/
docker exec postgres psql -U postgres -d jchatmind -f /tmp/memory_hub_migration.sql
```

连接信息（与 `application.yaml` 一致）：`localhost:5432`，用户 `postgres`，密码 `123456`，库 `jchatmind`。

**Ollama**（ARCHIVE 向量化）：`ollama pull bge-m3`，默认 `http://localhost:11434`

**诊断 API**：`GET /api/memory/status`、`GET /api/memory/stats/{sessionId}`

---

## 快速开始

### 1. 环境变量

```powershell
$env:OPEN_AI_API_KEY = "sk-..."   # DeepSeek
$env:ZHIPU_API_KEY = "..."        # 智谱
```

### 2. 依赖服务

- PostgreSQL（库名 `jchatmind`）
- 可选：MCP 代理（Coding 终端命令）

```powershell
# 启动 MCP（Windows）
cd JChatMind/jchatmind/scripts/mcp
.\start-mcp-proxy.ps1
```

`application.yaml` 中需开启：

```yaml
spring.ai.mcp.client.enabled: true
mcp.enabled: true
```

### 3. 启动后端

```powershell
cd JChatMind/jchatmind
mvn spring-boot:run
```

### 4. 启动前端

```powershell
cd JChatMind/ui
npm install
npm run dev
```

---

## MCP 全链路（简述）

```
start-mcp-proxy.ps1 (:3000 SSE)
  → Spring AI McpSyncClient
  → McpToolBridgeImpl（RecordingToolCallback）
  → McpIntegrationImpl（白名单 + 别名 run_terminal_cmd ↔ execute_command）
  → JChatMindFactory 注入 Agent
  → JChatMind reason/act 闭环
  → CodingMcpOutputBridge → 前端 CODING_COMMAND_OUTPUT SSE
```

联调诊断：`GET /api/coding/mcp-tools`

---

## 消息窗口（更新 2 重点）

| 配置 | 说明 |
|------|------|
| `chat_options.messageLength` | Agent 基线窗口（DB 加载 + 内存） |
| `coding.agent.memory-window` | Coding / 子 Agent 取 `max(messageLength, 80)` |
| `coding.agent.tool-aware-memory` | 按 assistant+tool 整轮淘汰，钉住 system 与首条 user |

避免 `MessageWindowChatMemory` 裁断 tool 链导致 DeepSeek/OpenAI 400 与上下文丢失。

---

## 已验证 E2E 场景

| 场景 | 工作区 | 要点 |
|------|--------|------|
| Python uv | `workspace/python-uv-e2e` | MCP `uv sync` + `uv run pytest` |
| 跳跳乐网页 | `workspace/jump-game-e2e` | MCP `dir` + 写 HTML/CSS/JS + 交付 |
| 贪吃蛇 | `workspace/snake-e2e` | 无 MCP 文件工具闭环 |

工作区根目录配置在 `application.yaml` → `coding.workspace.allowed-roots`（当前为 Z 盘路径）。

---

## 关键配置摘要

```yaml
spring.ai.deepseek.api-key: ${OPEN_AI_API_KEY}
spring.ai.zhipuai.api-key: ${ZHIPU_API_KEY}

coding:
  agent:
    max-loop-steps: 35
    memory-window: 80
    tool-aware-memory: true
  subagent:
    enabled: true
    worker-agent-name: Claude Code Coding Agent
```

---

## 许可证

见仓库根目录 [LICENSE](LICENSE)。

---

## 致谢

- 原项目：[代码随想录 JChatMind](https://programmercarl.com)
- 扩展方向：Claude Code 式自主开发、MCP-first 终端、多 Agent 编排
