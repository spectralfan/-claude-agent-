# AI Coding 联调配置指南

本文说明如何启用 **MCP 命令执行**、**RocketMQ 实时推送** 与 **Coding Agent 预设**，跑通 Python/Java 自主开发 MVP。

---

## 1. Coding Agent 预设

启动时自动创建（若不存在）名为 **「Claude Code Coding Agent」** 的智能体，工具白名单：

- `coding_file_tools`（含 `append_coding_file` 分块写入）
- `coding_search_tools`
- `coding_verify_tools`（**优先**结构化验证）
- `maven_command`（Java 降级）
- `mark_coding_complete`
- `execute_command` / `run_terminal_cmd` / `bash` / `shell`（MCP，仅简单命令）

配置：

```yaml
coding:
  agent-preset:
    enabled: true   # 关闭则不在启动时自动创建
```

API：`GET /api/coding/agents/preset` → 返回 `agentId`，前端创建 Coding 任务时可直接选用。

预设定义文件：`src/main/resources/coding-agent-preset.json`

---

## 1.1 多 Agent DAG 编排（Scheduler + Worker + Reviewer）

任务持久化到 PostgreSQL 表 `t_orchestration_task`，由 `OrchestrationTaskDispatcher` 按依赖驱动调度：

| 角色 | 预设名 | 工具 |
|------|--------|------|
| **Scheduler** | Claude Code Scheduler | `orchestration_task_tools`、`orchestration_read_tools`、`mark_coding_complete` |
| **Worker** | Claude Code Coding Agent | 文件/验证/MCP/`run_workspace_shell` |
| **Reviewer** | Claude Code Reviewer | `orchestration_read_tools`（只读） |

配置：

```yaml
coding:
  orchestrator-preset:
    enabled: true
  reviewer-preset:
    enabled: true
  orchestration:
    max-depth: 1
    max-concurrency: 4
    auto-review: true          # Worker COMPLETED 后自动插入 REVIEWER
    auto-review-depends: true
    dispatch-interval-ms: 2000
  subagent:
    enabled: true
    worker-agent-name: Claude Code Coding Agent
    max-loop-steps: 35
    pool-size: 4
    auto-continue: true
    max-auto-continuations: 30
  agent:
    max-loop-steps: 100
    memory-window: 80
    tool-aware-memory: true
```

**建表**（PostgreSQL 在 Docker，容器名 `postgres`，与 `application.yaml` 一致 `localhost:5432/jchatmind`）：

```powershell
docker cp JChatMind/jchatmind/src/main/resources/db/orchestration_task.sql postgres:/tmp/
docker exec postgres psql -U postgres -d jchatmind -f /tmp/orchestration_task.sql
```

验证：`docker exec postgres psql -U postgres -d jchatmind -c "\d t_orchestration_task"`

**子 Agent Prompt**：不含父 chat 历史；`composeRolePrompt` 注入角色模板 + goal + constraints + contextFiles（单文件 512KB 截断）。

API：

- `GET /api/coding/agents/orchestrator-preset` → Scheduler `agentId`
- `GET /api/coding/agents/preset` → Worker `agentId`
- `GET /api/coding/orchestration/tasks?sessionId=` → DAG 任务列表
- `GET /api/coding/tasks/session/{id}/subtasks` → 兼容同上

流程：

1. 创建 Coding 任务，选用 **Scheduler** Agent
2. `create_orchestration_task(role=WORKER, dependsOn=...)` 批量建图；无依赖任务并行 READY
3. Dispatcher 在 `max-concurrency` 内提交 Worker/Reviewer；Worker 完成后可自动插 Reviewer（`auto-review: true`）
4. `list_orchestration_tasks` 轮询；SSE `CODING_SUBTASK_*` 含 `role`、`dependsOn`
5. 全部终态后 `OrchestratorContinuationService` 自动续跑 Scheduler
6. `delegate_coding_task` 仍可用（薄包装创建 WORKER 任务）

预设：`coding-orchestrator-preset.json`、`coding-agent-preset.json`、`coding-reviewer-preset.json`

---

## 1.2 结构化验证（coding_verify_tools）

**优先于 MCP 拼 shell**，根治 `node -e`、HTML 路径 `node --check`、cmd 管道等问题：

| 工具方法 | 用途 |
|----------|------|
| `check_js_syntax` | 对 `.js` 文件跑 `node --check`（ProcessBuilder，非 MCP） |
| `verify_coding_file` | 确认工作区文件存在 |
| `run_allowed_verify` | 白名单：`npm test`、`npm run <script>`、`uv run pytest`、`node --check <file>` |

Agent 预设 `coding-agent-preset.json` 已勾选 `coding_verify_tools` 并在 systemPrompt 中要求优先使用。

**大文件写入**：HTML/JS 超过 ~8KB 时禁止单次 `write_coding_file`，改用 `append_coding_file` / `apply_coding_patch` 分块，避免 JSON 截断。

---

## 2. MCP（多语言命令验证）

### 2.0 安装 uv（Python 栈推荐）

```powershell
powershell -ExecutionPolicy Bypass -c "irm https://astral.sh/uv/install.ps1 | iex"
# 确保 %USERPROFILE%\.local\bin 在 PATH 中
uv --version
```

Python 栈使用 **uv 原生工作流**：`pyproject.toml` + `uv.lock`，`uv sync` / `uv add` / `uv run pytest`（勿用 pip）。

### 2.1 启动 mcp-proxy（Windows 推荐内置 shell）

**默认已随后端自动启动**（`mcp.proxy.auto-start: true`）。后端会在 `scripts/mcp` 下拉起：

```text
npx -y mcp-proxy --port 3000 --server sse -- node jchatmind-shell-mcp.mjs
```

若端口 3000 已被占用（例如你已手动运行 proxy），则跳过自动启动。

手动启动（调试 proxy 时）：

```powershell
cd JChatMind/jchatmind/scripts/mcp
.\start-mcp-proxy.ps1
```

关闭自动启动：在 `application.yaml` 设 `mcp.proxy.auto-start: false`。

**开发期推荐** `spring.profiles.active=dev`（`application-dev.yaml` 设 `mcp.proxy.stop-on-shutdown: false`，避免 DevTools 热重启杀 proxy）。

暴露工具：`execute_command`（后端自动别名 `run_terminal_cmd` / `bash` / `shell`）。

**MCP 执行链**（三层防护）：

```
RecordingToolCallback
  → McpShellCommandPolicy（Java 拦截高危命令）
  → McpShellArgumentEnricher（路径/命令改写）
  → jchatmind-shell-mcp.mjs
  → command-runner.mjs v2.3.1（PowerShell 执行 + 命令规范化）
```

自检：

```powershell
Invoke-RestMethod http://localhost:8080/api/coding/mcp-health
# 期望 runnerVersion: 2.3.1，selfTest: PASS

Invoke-RestMethod http://localhost:8080/api/coding/mcp-tools
# 期望 data.names 含 execute_command
```

### 2.2 开启 MCP 客户端

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        sse:
          connections:
            proxy:
              url: http://localhost:3000

mcp:
  enabled: true
  shell:
    platform: auto      # windows 开发机 → PowerShell
    executor: auto
    policy-enabled: true
```

### 2.3 Agent 工具

使用预设 Agent 即可；或手动在 Agent 管理页勾选上述工具名（须与 proxy 实际暴露名称一致，可开 `mcp.tool-name-prefix-strip: true`）。

---

## 3. 实时推送：local vs RocketMQ

浏览器仍通过 **SSE** 收消息（`GET /sse/connect/{sessionId}`）。  
业务侧统一调用 `ChatEventPublisher.publish(sessionId, message)`。

| 模式 | 配置 | 行为 |
|------|------|------|
| **local**（默认） | `realtime.messaging.mode: local` | 直接写入本机 SSE 连接 |
| **rocketmq** | `realtime.messaging.mode: rocketmq` | 写入 RocketMQ → 各实例广播消费 → 本机有 SSE 连接则投递 |

### 3.1 RocketMQ 模式

1. 启动 RocketMQ NameServer + Broker（默认 `127.0.0.1:9876`）
2. 修改配置：

```yaml
realtime:
  messaging:
    mode: rocketmq
    rocketmq:
      name-server: 127.0.0.1:9876
      topic: jchatmind-chat-events
      producer-group: jchatmind-chat-producer
      consumer-group: jchatmind-sse-bridge
```

3. 多实例部署时，每个实例都会收到 MQ 消息，仅向**本机已建立的 SSE** 投递（`MessageModel.BROADCASTING`）。

---

## 4. Coding 工作区

```yaml
coding:
  workspace:
    root: ./workspace
    allowed-roots:
      - name: 我的工程
        path: D:/projects/my-app
  approval:
    default-mode: development
```

创建任务（Claude Code 式，**无需手选技术栈**）：

1. 前端向导：选 **工作区** + **Agent** → 进入对话
2. 后端默认 `autoDetectStack: true`：有 `pom.xml` / `pyproject.toml` 等则在**首条消息时**自动绑定栈
3. 未识别时 Agent 自行读目录判断；用户也可在对话中说「用 Python + uv」等

可选 API 字段：

- `stackId` — 显式指定（一般不必）
- `scaffoldOnCreate: true` — 空目录从 `coding-templates` 脚手架

---

## 5. MVP 验证步骤

### 5.1 Python + uv 示例

1. 安装 **uv**（§2.0）、启动 PostgreSQL、后端、`scripts/mcp/start-mcp-proxy.ps1`
2. `GET /api/coding/agents/preset` 拿到 `agentId`
3. 工作区放在 Z 盘工程目录，例如 `Z:/JAVA_workshop/JChatMindv2/JChatMind/jchatmind/workspace/python-uv-e2e`（须在 `allowed-roots`）
4. 创建 Coding 任务：`stackId=python-pytest`，`scaffoldOnCreate=true`，`workspacePath=.`
5. 发送：「实现 todo CLI，用 uv run pytest 通过，完成后 mark_coding_complete」
6. 观察：MCP 执行 `uv sync` / `uv run pytest`、产出含 `pyproject.toml` 与 `uv.lock`、任务 `COMPLETED`

脚手架模板已含 `pyproject.toml`、`uv.lock`、示例 `tests/test_todo.py`。

### 5.2 HTML/JS 贪吃蛇（已实测 2026-06-02）

无需 MCP、无需 Maven，适合验证 **Worker 单 Agent 写文件 + 交付** 闭环：

1. 工作区：`jchatmind/workspace/snake-e2e/`（已在 `allowed-roots`）
2. Agent 选 **Worker**（非 Orchestrator）
3. 纯静态 HTML 时建议临时 `coding.delivery.require-verification: false`
4. 发送：「用 HTML+CSS+JS 写贪吃蛇，index.html 可直接打开，完成后 mark_coding_complete」
5. 预期产出：`index.html`、`style.css`、`game.js`，任务 `COMPLETED`

完整 API 序列、踩坑与脚本见 [`Phase3_架构设计.md` §19](./Phase3_架构设计.md#19-端到端测试案例贪吃蛇-htmljscss)。

**发消息注意**：`POST /api/chat-messages` 的 `role` 必须为 `"user"`（小写）。

---

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| Agent 不执行命令 | `mcp.enabled`、Agent `allowedTools`、proxy 是否在线 |
| 终端无输出 | 前端是否连 SSE；RocketMQ 模式 Broker 是否正常 |
| Java 能跑 Python 不能 | Python 依赖 MCP；确认 `execute_command` 在 mcp-tools 列表中 |
| uv 命令 MCP 失败 | `uv` 是否在 PATH；MCP 是否用 `start-mcp-proxy.ps1`（非 mkusaka） |
| workingDir 报错 | 工作区须在 Z 盘 `allowed-roots` 内，勿放用户 HOME 目录 |
| pip 很慢 | 已切换 uv；Skill 与栈配置均要求 `uv sync` / `uv run` |
| 脚手架为空 | 检查 `coding-templates/{stackId}/` 是否在 classpath |
| 应用启动失败（循环依赖） | 确认 `CodingSubtaskExecutorImpl` 使用 `@Lazy`；`spring.main.allow-circular-references: true` |
| API 发消息 Agent 不跑 | 检查 `role` 是否为 `"user"`（非 `"USER"`） |
| HTML 任务无法 mark_coding_complete | 用 `coding_verify_tools`；或 `require-verification: false` |
| `node --check` 对 HTML 失败 | 勿经 MCP；用 `check_js_syntax` 或 `verify_coding_file` |
| JSON 截断 / Unexpected end-of-input | 大文件分块 `append_coding_file`，勿单次 write 整 HTML |
| 8080 already in use | DevTools 旧进程未退出；杀 Java 进程或 `spring.profiles.active=dev` |
| MCP 自检 WARN | 查 `GET /api/coding/mcp-health`；重启后端 + proxy |
| DB 里旧 Agent 缺工具 | 手动同步 `allowedTools`（含 `coding_verify_tools`）或删库让预设重建 |
| DeepSeek HTTP 401 / `****KEY}` | Key 未注入 JVM：见下文 **§8 LLM API Key** |

---

## 8. LLM API Key（DeepSeek 401 排查）

`application.yaml` 使用 `spring.ai.deepseek.api-key: ${DEEPSEEK_API_KEY}`，**仅**从环境变量或本地覆盖文件读取。

**常见根因**：Windows 已在「系统变量」配置 `DEEPSEEK_API_KEY`，但 IDE/Cursor 启动早于变量设置，或 Run Configuration 未把系统环境传给 Java 进程，导致占位符未解析（日志里 api key 尾部像 `KEY}`）。

**推荐启动方式**（自动从用户/系统环境变量注入）：

```powershell
cd JChatMind/jchatmind
.\scripts\run-backend.ps1
```

**自检**（新 PowerShell 窗口）：

```powershell
# 应至少有一项非空
[System.Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY','User')
[System.Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY','Machine')
$env:DEEPSEEK_API_KEY
```

启动成功后日志应出现：`DeepSeek API Key 已加载（length=35）`（长度因 Key 而异）。

**备选：本地文件**（勿提交 git）：

```powershell
Copy-Item src/main/resources/application-local.yaml.example application-local.yaml
# 编辑 application-local.yaml 填写 spring.ai.deepseek.api-key
mvn spring-boot:run
```

**IntelliJ IDEA**：Run Configuration → Environment variables 添加 `DEEPSEEK_API_KEY`，或勾选传递系统环境；修改系统变量后需**完全退出并重启 IDE**。

---

## 7. 相关 API

| 接口 | 说明 |
|------|------|
| `GET /api/coding/stacks` | 技术栈列表 |
| `GET /api/coding/workspaces/detect` | 自动识别栈 |
| `GET /api/coding/agents/preset` | Coding Agent 预设 |
| `GET /api/coding/tasks/{id}/summary` | 交付摘要 |
| `GET /api/coding/tasks/session/{id}/subtasks` | 子任务列表 |
| `GET /api/coding/runtime-status` | MCP / 交付验证开关 |
| `GET /api/coding/mcp-health` | command-runner 版本与 smoke 自检 |
| `GET /api/coding/mcp-tools` | 当前 MCP 桥接发现的工具名 |
| `POST /api/coding/tasks/{id}/run-shell` | 栈感知 Shell 验证 |
| `GET /sse/connect/{sessionId}` | 浏览器 SSE（最终收消息入口） |
