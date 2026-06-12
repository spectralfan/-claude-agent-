# JChatMindv2 — AI 编程 Agent 平台

> 基于 Scheduler-Worker-Reviewer 三层编排的智能编码辅助系统。

## 强制开发规范（每次会话自动生效）

本项目的开发必须遵循以下宪章规则。**所有代码变更前必须按此流程执行。**

### 变更类型判断

进入工作前先判断变更类型：

| 类型 | 标准 | 流程 |
|------|------|------|
| **A: 大功能/新特性** | 新增文件 > 3 个或涉及新模块 | 完整 OpenSpec + Superpowers 流程 |
| **B: 小修改/Bug修复** | 修改文件 ≤ 3 个 | Superpowers 简化版 |
| **C: 单行/文档级** | 仅文档/配置/单行代码 | 直接修改 + 验证 + 提交 |

### 类型 A 必须执行的三段流程

**1. OpenSpec 阶段：** 规格管理
- 初始化：openspec init（仅首次）
- 启动变更：/opsx:new <name> → 生成 proposal / specs / design / tasks
- 快速生成：/opsx:ff <name>

**2. Superpowers 执行阶段：**
- rainstorming → 设计文档到 docs/superpowers/specs/
- writing-plans → 计划到 docs/superpowers/plans/
- TDD 红-绿-重构循环 → 测试代码 + 实现代码
- erification-before-completion → 逐项确认
- low-review → 规格合规 + 代码质量双审查

**3. OpenSpec 收尾：**
- /opsx:verify → 验证报告
- /opsx:archive → 归档变更

### TDD 铁律
- 先写测试再写实现
- 红（失败测试）→ 绿（最少代码通过）→ 重构
- 未经测试的代码不存在

### 红线（禁止行为）
| 禁止 | 正确做法 |
|------|----------|
| "这只是简单问题，不需要技能" | 先检查技能再回应 |
| "我先查查代码库" | 先用技能 |
| "不需要测试" | TDD 铁律 |
| "先实现再完善" | 规格先行，设计先行 |

---

## Project

| 属性 | 值 |
|------|-----|
| 后端 | Java 17 + Spring Boot 3.5.8 + Spring AI 1.1 + PostgreSQL 16 + MyBatis |
| 前端 | React + TypeScript + Vite + Ant Design X |
| LLM | DeepSeek / 智谱 GLM |
| MCP | mcp-proxy SSE + Node.js shell-mcp |
| 入口 | JchatmindApplication.java / main.tsx |

## Commands

`ash
# 后端
cd JChatMind/jchatmind && ./mvnw spring-boot:run  # :8080
cd JChatMind/jchatmind && ./mvnw compile           # 编译
cd JChatMind/jchatmind && ./mvnw test -Dtest="ClassName"  # 测试

# 前端
cd JChatMind/ui && npm run dev     # 开发
cd JChatMind/ui && npm run build   # 构建

# 外部依赖
docker run -d --name postgres -e POSTGRES_PASSWORD=123456 -p 5432:5432 postgres:16
npx mcp-proxy --port 3000          # MCP proxy
pwsh scripts/e2e/coding-tank-game-e2e.ps1  # E2E
`

## Architecture

`
用户请求 -> Scheduler(拆解DAG) -> Worker(并行编码) -> Reviewer(审查) -> 完成
`

| 模块 | 包路径 | 职责 |
|------|--------|------|
| Agent | gent/ | JChatMind, Factory, Profile YAML, 17+ Tools |
| Coding | coding/ | DAG调度, 任务执行, 续跑, 审批, 验证 |
| Session | session/ | ThreadStore/NoteStore, EventBus, AgentLoop, SessionManager |
| Memory | memory/ | RAG (Ollama bge-m3 + PgVector) |
| MCP | mcp/ | 别名注册, 命令策略, 集成 |
| UI | ui/ | 普通对话 / AI Coding / 知识库三路由 |

## Conventions

- **TDD**: 先写测试再实现，红-绿-重构
- **中文**: 文档/commit/注释用中文，代码标识符用英文
- **设计先行**: 大功能先出 docs/superpowers/specs/
- **Agent Profile**: 角色定义在 gent-profiles/*.yaml
- **Session 类型**: 按 Agent 工具自动检测 CHAT/CODING
- **Shell 执行**: 规范名 ash
- **Graphify + Codegraph**: 架构查询双引擎

## Notes