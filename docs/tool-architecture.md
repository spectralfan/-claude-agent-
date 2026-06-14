# JChatMindv2 架构文档

## 项目概述
JChatMindv2 是一个 AI 编程 Agent 平台，基于 Spring Boot 3 + React 前端 + PostgreSQL 构建。

## 核心模块

### 1. Agent 系统 (`agent/`)
- **JChatMind** — Agent 运行时核心：reason→act→observe 循环
- **JChatMindFactory** — Agent 实例工厂，管理配置/工具/记忆组装
- **AgentProfileLoader** — 从 YAML 加载 Agent 角色配置（planner/worker/reviewer）
- **ToolRegistry** — 工具注册与权限过滤

### 2. 工具系统 (`agent/tools/`)
- **SpawanAgentTool** — 子 Agent 委派（前台阻塞 / 后台并行），嵌套深度 1 层
- **WriteFileTool** — 写入文件（工作区边界检查）
- **ReadFileTool / BatchReadTool** — 读取文件
- **ListDirTool** — 列出目录（list_coding_directory_tree / list_coding_directory）
- **DirectAnswerTool / TerminateTool** — 对话控制
- **NoteSaveTool** — 保存笔记

### 3. MCP 集成 (`mcp/`)
- **jchatmind-shell-mcp.mjs** — 纯 bash MCP Server（v3.0.0，Git Bash 执行）
- **McpClientManager** — MCP 客户端管理
- **RecordingToolCallback** — 工具调用记录 + 权限审批
- **PermissionManager** — 4 层权限策略（deny→allow→ask→审批）

### 4. 事件系统 (`session/event/`)
- **EventBus** — 内存事件总线
- **RpcEventBridge** — EventBus → WebSocket 桥接
- 事件类型：StepStarted/Finished, ToolCalled/Result, SubagentStarted/Finished, PermissionRequested

### 5. Coding 系统 (`coding/`)
- **CodingTask** — 编码任务（工作区、状态、元数据）
- **CodingWorkspaceService** — 工作区管理
- **CodingStack / CodingSkill** — 技术栈配置与自主开发技能
- **CodingPromptComposer** — 动态 Prompt 组装

### 6. 记忆系统 (`memory/`)
- **MemoryHub** — 会话记忆（session/thread/notes）
- **MemoryIntegration** — 记忆注入 Agent 上下文

### 7. 前端 (`ui/`)
- React + Vite + TypeScript
- WebSocket 实时事件流
- Agent 聊天 / AI Coding 双视图
- 子 Agent 进度卡片
- **PermissionDialog** — 工具调用审批弹窗

## 删除的模块（清理后）
- MCP Proxy（SSE 代理）
- 编排系统（Orchestrator/DAG）
- PowerShell 兼容层（command-runner.mjs）
- 死工具：CodingFileTools, CodingVerifyTools, CodingSearchTools, CodingRunTool, GitTools
- 别名系统：McpToolAliasRegistry, AliasAwareToolCallbackResolver, FallbackTerminalToolCallback

## 关键架构决策
1. **纯 bash MCP** — 放弃 PowerShell，所有命令通过 Git Bash 执行
2. **Profile 驱动 Agent** — planner/worker/reviewer 角色由 YAML 配置，KamaClaude 风格
3. **权限审批** — execute_command / write_coding_file 触发前端弹窗
4. **无编排** — Agent 自主决策工具使用，不强制工作流
