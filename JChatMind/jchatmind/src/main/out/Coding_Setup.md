# AI Coding 联调配置指南

本文说明如何启用 **MCP 命令执行**、**RocketMQ 实时推送** 与 **Coding Agent 预设**，跑通 Python/Java 自主开发 MVP。

---

## 1. Coding Agent 预设

启动时自动创建（若不存在）名为 **「Claude Code Coding Agent」** 的智能体，工具白名单：

- `coding_file_tools`
- `maven_command`（Java 降级）
- `mark_coding_complete`
- `execute_command` / `run_terminal_cmd` / `bash` / `shell`（MCP）

配置：

```yaml
coding:
  agent-preset:
    enabled: true   # 关闭则不在启动时自动创建
```

API：`GET /api/coding/agents/preset` → 返回 `agentId`，前端创建 Coding 任务时可直接选用。

预设定义文件：`src/main/resources/coding-agent-preset.json`

---

## 1.1 异步子 Agent（Orchestrator + Worker）

编排 Agent 将子目标委派给后台 Worker，父 Agent 通过工具轮询结果：

| 角色 | 预设名 | 工具 |
|------|--------|------|
| **Orchestrator** | Claude Code Orchestrator | `delegate_coding_task`、`coding_subtask_tools` |
| **Worker** | Claude Code Coding Agent | 文件/MCP/交付工具（不含委派） |

配置：

```yaml
coding:
  orchestrator-preset:
    enabled: true
  subagent:
    enabled: true
    worker-agent-name: Claude Code Coding Agent
    max-loop-steps: 35
  agent:
    memory-window: 80          # Coding/子 Agent 实际窗口 = max(messageLength, 80)
    tool-aware-memory: true    # 按 tool 轮次裁剪，避免 assistant/tool 链断裂
```

**消息窗口说明**：Agent 表 `chat_options.messageLength` 为基线窗口；Coding 任务或子 Agent 运行时取 `max(messageLength, coding.agent.memory-window)`。启用 `tool-aware-memory` 后使用 `ToolAwareMessageWindowChatMemory`，按「assistant+tool_calls + tool 响应」整轮淘汰，并钉住 system 与首条 user（子任务 goal）。

API：

- `GET /api/coding/agents/orchestrator-preset` → 编排 Agent `agentId`
- `GET /api/coding/agents/preset` → Worker Agent `agentId`

流程：

1. 创建 Coding 任务，选用 **Orchestrator** Agent
2. 父 Agent 调用 `delegate_coding_task(goal, title)` → 立即返回 `subTaskId`
3. 后台 `@Async` 启动 Worker 完整 Agent loop（共享父任务工作区）
4. 父 Agent 用 `get_coding_subtask_status` / `list_coding_subtasks` 轮询
5. SSE 推送 `CODING_SUBTASK_STARTED/COMPLETED/FAILED` 到父会话

预设定义：`coding-orchestrator-preset.json`

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

项目自带 Windows 友好 MCP shell（`cmd.exe`，支持 `workingDir`），避免 `@mkusaka/mcp-shell-server` 的 bash 包装问题：

```powershell
cd JChatMind/jchatmind/scripts/mcp
.\start-mcp-proxy.ps1
```

等价命令：

```text
npx -y mcp-proxy --port 3000 --server sse -- node jchatmind-shell-mcp.mjs
```

暴露工具：`execute_command`（后端自动别名 `run_terminal_cmd` / `bash` / `shell`）。

自检：

```powershell
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
  enabled: true   # 按 Agent allowedTools 白名单注入 MCP 工具
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

创建任务时可传：

- `stackId`: `java-maven` / `python-pytest` / `node-npm`
- `autoDetectStack: true` — 按 `pom.xml` / `pyproject.toml` 等识别
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
| HTML 任务无法 mark_coding_complete | MCP 关闭时无验证记录；临时 `require-verification: false` 或 UI 手动 run-shell |

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
| `GET /api/coding/mcp-tools` | 当前 MCP 桥接发现的工具名 |
| `POST /api/coding/tasks/{id}/run-shell` | 栈感知 Shell 验证 |
| `GET /sse/connect/{sessionId}` | 浏览器 SSE（最终收消息入口） |
