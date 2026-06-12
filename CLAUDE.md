# CLAUDE.md — 开发工作流宪章

> 本文件是项目的「开发宪法」，定义 OpenSpec（规格管理）与 Superpowers（执行纪律）的融合
> 开发方法论。Agent 每次启动时必须加载此文件并按此流程工作。

---

## 1. 项目身份

| 项目 | 值 |
|------|-----|
| 工作区 | Reasonix 全局工作区 |
| AI 助手 | Reasonix（子代理模式） |
| 规格框架 | OpenSpec v1.4+ |
| 执行方法论 | Superpowers（7 步标准工作流） |
| 语言 | 中文（设计文档、提交信息、注释） |
| 开发纪律 | TDD 铁律（红-绿-重构循环） |

---

## 2. 融合工作流宪章

### 2.1 核心原则

| 原则 | 说明 |
|------|------|
| **规格先行** | 任何代码变更前必须先有规格说明。OpenSpec 管理规格生命周期。 |
| **执行纪律** | 实现阶段严格遵循 Superpowers 7 步流程：理解→探索→拆分→计划→实现→验证→提交。 |
| **TDD 铁律** | 先写测试再写实现，红-绿-重构循环。未经测试的代码不存在。 |
| **小步快走** | 每个任务 2-5 分钟，频繁 commit，一次一个变更。 |
| **中文优先** | 所有文档和提交信息使用中文，代码标识符使用英文。 |

### 2.2 融合流程总览

```
                   ┌─────────────────────────────────────┐
                   │         用户提出需求/变更              │
                   └──────────────────┬──────────────────┘
                                      │
                    ┌─────────────────▼─────────────────┐
                    │      判断变更类型（见 2.3）          │
                    └────────┬────────────┬──────────────┘
                             │            │
                ┌────────────▼──┐    ┌────▼────────────┐
                │  大功能/新特性  │    │ 小修改/Bug修复   │
                │  (类型 A)      │    │  (类型 B)        │
                └────────┬──────┘    └────┬────────────┘
                         │                │
              ┌──────────▼──────────┐    ┌▼──────────────────┐
              │ OpenSpec 先         │    │ Superpowers 调试   │
              │ 提案→规格→设计→任务  │    │ 直接定位→修复→验证  │
              └──────────┬──────────┘    └┬──────────────────┘
                         │                │
              ┌──────────▼──────────┐    │
              │ Superpowers 执行     │    │
              │ 头脑风暴→计划→TDD    │    │
              │ →子代理→验证→评审    │    │
              └──────────┬──────────┘    │
                         │                │
              ┌──────────▼──────────┐    │
              │ OpenSpec 收尾       │    │
              │ 验证→归档           │    │
              └──────────┬──────────┘    │
                         │                │
                         └────────┬───────┘
                                  │
                      ┌───────────▼───────────┐
                      │  提交准备/分支整理      │
                      │  commit→PR→审查        │
                      └───────────────────────┘
```

### 2.3 变更类型判断

进入流程前，先判断变更类型：

| 类型 | 判断标准 | 走哪条路径 |
|------|---------|-----------|
| **A: 大功能/新特性** | 新增文件 > 3 个，或涉及新模块/新系统 | 完整融合流程 |
| **B: 小修改/Bug修复** | 修改文件 ≤ 3 个，逻辑简单，无架构变化 | Superpowers 简化版 → OpenSpec 归档 |
| **C: 单行/文档级** | 仅修改文档、配置、单行代码 | 直接修改 + 验证 + 提交 |

---

### 3. 类型 A：大功能/新特性 — 完整融合流程

这是核心流程，分为 **OpenSpec 阶段 → Superpowers 阶段 → OpenSpec 收尾** 三段。

#### 3.1 OpenSpec 阶段：规格管理（提案→规格→设计→任务）

| 步骤 | Skill / 命令 | 产出物 |
|------|-------------|--------|
| 1. 初始化 OpenSpec（仅首次） | `openspec-initial` 或 `openspec init` | `openspec/config.yaml` |
| 2. 探索想法 | `openspec-explore` 或 `/opsx:explore` | 对话式探索，无制品 |
| 3. 启动变更 | `openspec-new` 或 `/opsx:new <name>` | `openspec/changes/<name>/.openspec.yaml` |
| 4. 快速生成规划制品 | `openspec-ff` 或 `/opsx:ff <name>` | 提案 / 规格 / 设计 / 任务 四个文件 |
|    — 或逐个创建 — | `openspec-continue` 或 `/opsx:continue` | 逐个创建各制品 |

**产出物检查清单：**
- [ ] `openspec/changes/<name>/proposal.md` — 变更提案
- [ ] `openspec/changes/<name>/specs/` — 规格说明
- [ ] `openspec/changes/<name>/design.md` — 设计方案
- [ ] `openspec/changes/<name>/tasks.md` — 任务列表

> **完成标志**：`tasks.md` 已生成，包含可执行的复选框任务列表。

#### 3.2 Superpowers 阶段：执行实现（计划→TDD→子代理→验证）

| 步骤 | Skill | 产出物 |
|------|-------|--------|
| 5. 头脑风暴细化 | `brainstorming` | `docs/superpowers/specs/YYYY-MM-DD-<name>-design.md` |
| 6. 编写实现计划 | `writing-plans` | `docs/superpowers/plans/YYYY-MM-DD-<name>.md` |
| 7. TDD 实现 | `test-driven-development` | 红-绿-重构：测试代码 + 实现代码 |
| 8. 子代理驱动开发 | `subagent-driven-development` | 分派子代理逐个执行任务 |
| 9. 完成前验证 | `verification-before-completion` | 逐项确认任务完成 |
| 10. 系统调试（如需） | `systematic-debugging` | 问题定位与修复 |
| 11. 流程审查 | `flow-review` | 规格合规 + 代码质量双审查 |

**细化说明：**

**步骤 5-6（计划阶段）：**
- 调用 `brainstorming` 理解需求细节，输出设计文档到 `docs/superpowers/specs/`
- 调用 `writing-plans` 将设计拆分为小步骤任务，输出计划到 `docs/superpowers/plans/`

**步骤 7-8（实现阶段）：**
- 以 TDD 方式实现：先写失败的测试，再写最少代码让测试通过，然后重构
- 子代理每个任务独立分派，一次一个不并行
- 每次通过测试后立即 commit

**步骤 9-11（验证阶段）：**
- `verification-before-completion` 确保所有任务完成
- `systematic-debugging` 仅在测试失败时调用
- `flow-review` 做两轮审查：规格合规 + 代码质量

> **完成标志**：所有代码已实现，测试通过，任务列表全部 `[x]`。

#### 3.3 OpenSpec 收尾（验证→归档）

| 步骤 | Skill / 命令 | 产出物 |
|------|-------------|--------|
| 12. 验证实现 | `openspec-verify` 或 `/opsx:verify` | 验证报告（CRITICAL / WARNING / SUGGESTION） |
| 13. 修复关键问题 | （手动） | 代码更新 |
| 14. 归档变更 | `openspec-archive` 或 `/opsx:archive` | 变更归档到 `openspec/changes/archive/` |

> **完成标志**：变更已归档，规格合并回主线，分支准备提交。

---

### 4. 类型 B：小修改/Bug 修复 — 简化流程

| 步骤 | Skill / 命令 | 产出物 |
|------|-------------|--------|
| 1. 系统调试 | `systematic-debugging` | 问题定位报告 |
| 2. 修复实现 | （TDD 方式） | 测试 + 修复代码 |
| 3. 完成前验证 | `verification-before-completion` | 确认修复完成 |
| 4. OpenSpec 归档 | `openspec-archive` 或 `/opsx:archive` | 归档变更 |

如果变更不涉及 OpenSpec 变更（没有预先创建 change），可以直接 commit。

---

### 5. 类型 C：单行/文档级修改

直接修改 + 验证 + 提交，跳过所有技能流程。

---

## 6. 技能调用规则

### 6.1 强制调用

**在任何响应或操作之前调用相关或被请求的技能。** 哪怕只有 1% 的可能性某个技能适用，都必须调用它检查。

### 6.2 技能优先级

1. **用户的明确指令**（CLAUDE.md、直接请求）— 最高优先级
2. **Reasonix 技能** — 在冲突处覆盖默认系统行为
3. **默认系统提示** — 最低优先级

### 6.3 常用技能索引

| 场景 | 技能名称 | 说明 |
|------|---------|------|
| 全局入口 | `using-superpowers` | 确立如何查找和使用技能 |
| 需求理解 | `brainstorming` | 将想法转化为设计 |
| 编写计划 | `writing-plans` | 多步骤实现计划 |
| TDD 开发 | `test-driven-development` | 红-绿-重构循环 |
| 子代理实现 | `subagent-driven-development` | 分派子代理执行任务 |
| 系统调试 | `systematic-debugging` | 定位并修复问题 |
| 完成验证 | `verification-before-completion` | 逐项确认完成 |
| 流程审查 | `flow-review` | 规格 + 代码双审查 |
| 中文提交 | `chinese-commit-conventions` | 中文 commit 规范 |
| 中文审查 | `chinese-code-review` | 代码审查标准 |
| 中文文档 | `chinese-documentation` | 文档编写规范 |
| 分支管理 | `finishing-a-development-branch` | 开发分支整理与提交 |
| 技能编写 | `writing-skills` | 编写自定义技能 |
| DUE 框架 | `due-framework` | 深度思考框架 |
| Git Worktree | `using-git-worktrees` | 专用 worktree 操作 |

### 6.4 OpenSpec 命令索引

| 命令 | 对应 skill | 说明 |
|------|-----------|------|
| `openspec init` | `openspec-initial` | 初始化项目 |
| `/opsx:explore` | `openspec-explore` | 探索想法 |
| `/opsx:new` | `openspec-new` | 启动新变更 |
| `/opsx:continue` | `openspec-continue` | 逐个创建制品 |
| `/opsx:ff` | `openspec-ff` | 快速生成所有规划制品 |
| `/opsx:apply` | `openspec-apply` | 按任务列表实现 |
| `/opsx:verify` | `openspec-verify` | 验证实现 |
| `/opsx:archive` | `openspec-archive` | 归档变更 |
| `openspec config` | `openspec-config` | 配置管理 |
| `openspec schema` | `openspec-schema` | 自定义 schema |

---

## 7. 代码智能工具（Code Intelligence）— Graphify × Codegraph 双引擎

本项目集成两套代码分析工具，互为补充：

| 工具 | 类型 | 定位 | 数据位置 |
|------|------|------|----------|
| **Graphify** | 外部 MCP 工具（`user-graphify`） | 语义图谱：模块概览、社区聚类、自然语言搜索 | `graphify-out/` |
| **Codegraph** | Reasonix 内置工具 | 精确符号索引：调用链、影响分析、符号跳转 | `.codegraph/codegraph.db` |

### 7.1 协作工作流

```
┌─ 想了解项目架构/模块划分 ──────────────────────┐
│  → graphify: query_graph / get_community       │
│  → 找到关键文件后，切到 codegraph 查内部符号      │
└────────────────────────────────────────────────┘

┌─ 想追踪精确调用链 ──────────────────────────────┐
│  → codegraph: trace / callers / callees         │
│  → 想了解文件所属模块 → graphify: get_node      │
└────────────────────────────────────────────────┘

┌─ 改代码前评估影响 ──────────────────────────────┐
│  → codegraph: impact（精确的引用断裂分析）        │
│  → graphify: 确认是否涉及核心社区（hub 节点）     │
└────────────────────────────────────────────────┘

┌─ 自然语言搜索（"哪里处理了用户权限？"） ─────────┐
│  → graphify: query_graph（语义搜索）             │
│  → 定位文件后 → codegraph: context 深入理解      │
└────────────────────────────────────────────────┘
```

### 7.2 Graphify 命令速查

| 用途 | 命令/工具 |
|------|----------|
| 更新图谱（AST 级） | 在项目根目录运行 `graphify update .` |
| 更新图谱（含语义） | `graphify update . --semantic` |
| 自然语言查询 | `query_graph("你的问题")` |
| 查看社区 | `get_community(社区ID)` |
| 查看节点 | `get_node(节点ID)` |
| 查看中枢节点 | `god_nodes` |

### 7.3 Codegraph 工具速查

| 用途 | 工具 |
|------|------|
| 入口点 + 相关符号概览 | `codegraph_context` |
| 符号搜索 | `codegraph_search` |
| 精确调用链追踪 | `codegraph_trace(起点, 终点)` |
| 影响分析 | `codegraph_impact(符号名)` |
| 调用者/被调用者 | `codegraph_callers` / `codegraph_callees` |

> **原则**：架构概览优先用 Graphify，精确分析用 Codegraph。Agent 在收到代码相关问题时，应判断问题类型，选择最合适的引擎——或组合使用。

## 8. 文档规约

| 项目 | 规约 |
|------|------|
| 设计文档位置 | `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md` |
| 实现计划位置 | `docs/superpowers/plans/YYYY-MM-DD-<feature-name>.md` |
| OpenSpec 规格 | `openspec/changes/<name>/` 下各制品 |
| 设计文档语言 | 中文 |
| 代码注释语言 | 中文 |
| commit 语言 | 中文 |
| commit 格式 | 见 `chinese-commit-conventions` |
| 分支命名 | `feature/<kebab-case>` 或 `fix/<kebab-case>` |

---

## 9. 红线（Agent 禁止行为）

| 禁止行为 | 正确做法 |
|---------|---------|
| "这只是简单问题，不需要技能" | 先检查技能，再回应 |
| "我先查查代码库" | 先用技能，技能告诉你怎么查 |
| "设计批准了，直接实现吧" | 还有文档/自检/审查/writing-plans 四步必须走完 |
| "改动很小，事后补文档" | 先补文档再实现 |
| "不需要测试" | TDD 铁律：先写测试再写实现 |
| "先实现再完善" | 规格先行，设计先行 |

---

## 10. 变更记录

| 日期 | 变更 | 作者 |
|------|------|------|
| YYYY-MM-DD | 初始创建 | Reasonix |
| 2026-06-11 | 新增第7节：Graphify × Codegraph 双引擎协作工作流 | Reasonix |
| 2026-06-12 | Session 管理层：ThreadStore / NoteStore / SessionManager / 消息 API 切换 | Reasonix |
| 2026-06-12 | Agent 编排优化：Profile 配置化 / SubAgentExecutor 轻量引擎 / EventBus + AgentLoop / Continuation 改进 | Reasonix |
| 2026-06-12 | 前端/后端重构：Session 类型化（CHAT/CODING）+ 路由分离 + 对话与编码双入口 | Reasonix |
| 2026-06-12 | MCP 工具优化：Shell 统一(规范化bash) + Git工具(git_status/diff/log/commit) + read_url + ToolResult/BaseTool 标准化 | Reasonix |
| 2026-06-12 | DB 优化：加索引、删14张电商表、统一UUID类型、删除时清理文件 + Session分类 + MCP STDIO直连 + 路由参数修复 | Reasonix |





