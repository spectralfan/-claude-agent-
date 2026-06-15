# JChatMindv2 架构文档 (Updated 2026-06-15)

## 项目概述
JChatMindv2 是一个 AI 编程 Agent 平台，基于 Spring Boot 3 + React + TypeScript + PostgreSQL 构建。

## 核心架构（KamaClaude 风格）

### Agent 执行循环
- **JChatMind** — plan→act→observe 循环，与 KamaClaude AgentLoop 对齐
- **JChatMindFactory** — Agent 实例工厂，配置工具/记忆/系统 prompt
- **AgentProfile** — YAML 驱动的角色配置（planner/worker/reviewer）

### 工具系统
- **SpawnAgentTool** — 子 Agent 委派（前台阻塞/后台并行），深度限制 1 层
- **WriteFileTool / ReadFileTool / BatchReadTool** — 文件读写（工作区边界检查）
- **ListDirTool** — 目录列出
- **ToolRegistry** — 统一工具注册与权限过滤

### MCP 集成
- **jchatmind-shell-mcp (v3.0.0)** — 纯 bash 执行，Git Bash on Windows
- **McpClientManager** — STDIO 直连 MCP 客户端管理
- **RecordingToolCallback** — 工具调用记录 + 权限审批拦截

### 权限系统
- **PermissionManager** — ask/auto 双模式 + 4 层策略（deny→allow→ask→审批）
- **PermissionController** — REST API（GET/PUT /mode, POST /respond）
- **PermissionDialog** — 前端审批弹窗

### WebSocket 事件系统
- **EventBus + RpcEventBridge** — 内存事件总线 → WebSocket 广播
- 事件类型：RunStarted/Finished, StepStarted/Finished, ToolCalled/Result, SubagentStarted/Finished, PermissionRequested

### Coding 系统
- **CodingTaskService** — 编码任务管理（工作区、状态、元数据）
- **CodingPromptComposerImpl** — 简化为仅注入 task 上下文（taskId + workspace 路径）
- **CodingStack / CodingSkill** — 技术栈与自主开发技能配置

### 前端
- React + Vite + TypeScript + Ant Design X
- WebSocket 事件流
- Agent 对话 / AI Coding 双视图
- 权限模式切换（Agent 对话头部）

## 已清理的模块
- MCP Proxy (SSE 代理)
- 编排系统 (Orchestrator/DAG)
- PowerShell 兼容层 (command-runner.mjs)
- 15+ 死工具：CodingFileTools, CodingVerifyTools, CodingSearchTools, CodingRunTool, GitTools
- 别名系统：McpToolAliasRegistry, AliasAwareToolCallbackResolver, FallbackTerminalToolCallback
- 死 prompt 注入：buildSchedulerContextBlock, buildWorkerSubtaskBlock, buildReviewerRoleBlock

## 工具清单

| 工具名 | 类型 | 说明 |
|--------|------|------|
| spawn_agent | 本地 | 创建子 Agent，前台/后台模式 |
| agent_result | 本地 | 查询后台 Agent 结果 |
| execute_command | MCP | bash 命令执行 |
| write_coding_file | 本地 | 写入文件 |
| append_coding_file | 本地 | 追加文件 |
| read_coding_file | 本地 | 读取文件 |
| read_coding_files | 本地 | 批量读取 |
| list_coding_directory_tree | 本地 | 递归列出目录 |
| list_coding_directory | 本地 | 单层列出 |
| direct_answer | 本地 | 直接回答 |
| terminate | 本地 | 结束任务 |
| save_note | 本地 | 保存笔记 |
| databaseQuery | 本地 | 数据库查询 |