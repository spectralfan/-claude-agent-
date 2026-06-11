---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 991079366132171_0-data_volume/7645520879681585458-files/所有对话/主对话/Phase3_架构设计_Prompt.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 991079366132171#1780282725185
    ReservedCode2: ""
---
# JChatMind Phase 3 - Coding Agent 架构设计 Prompt

## 目标

基于现有 JChatMind 架构（Think-Execute Agent + MCP + Memory Hub），实现一个**仿 Claude Code 的网页版 Coding Agent**。

---

## 核心约束

1. **前端**：网页对话模式，类 ChatGPT 体验（可扩展 Web Terminal）
2. **语言**：仅支持 Java
3. **执行环境**：本地，无 Docker（复用现有 `SandboxCommandRunner`）
4. **Agent 模式**：复用现有 Think-Execute 架构
5. **文件操作**：复用 MCP filesystem 工具（已接入 mcp-proxy-server）
6. **部署**：本地开发优先

---

## 新增模块

```
jchatmind/
├─ agent/tools/coding/           # Coding 专用工具
│  └─ CodingRunTool              # 命令执行（Maven 白名单）
│
├─ coding/                       # Coding 业务逻辑
│  ├─ CodingWorkspaceService    # 工作区管理、路径校验
│  ├─ CodingApprovalService     # 审批流
│  ├─ ProjectRulesService        # JCHATMIND.md / CLAUDE.md
│  └─ CodingTaskService         # 任务状态管理
│
└─ message/coding/              # Coding 专用 SSE 消息类型
   └─ CodingApprovalMessage
```

---

## 1. CodingRunTool（新增）

### 职责
执行 Maven 命令（白名单控制），支持审批流。

### 白名单
```
mvn compile
mvn test
mvn test -Dtest={TestClass}
mvn package -DskipTests
mvn clean compile
mvn clean test
```

### 参数
```java
@Tool(description = "执行 Maven 命令进行编译和测试")
public String runMavenCommand(
    @ToolParam(description = "Maven 命令，如 compile / test / package") 
    String goal
)
```

### 安全控制
- **白名单校验**：仅允许上述命令
- **超时**：单次执行 5 分钟
- **输出限制**：返回前 2000 字符
- **路径锁定**：只能在 `coding.workspace.root` 下执行
- **审批**：写操作后执行需审批

### 错误处理
- 超时返回：`"命令执行超时（5分钟）"`
- 白名单拒绝：`"命令不在白名单允许范围内"`
- 执行失败：返回 stderr 输出

---

## 2. CodingWorkspaceService

### 职责
- 工作区根目录管理（`coding.workspace.root`）
- 路径安全校验（防止路径穿越）
- 工作区初始化（可选：克隆空项目模板）

### 核心方法
```java
// 获取工作区根目录
Path getWorkspaceRoot();

// 安全路径校验，不允许 ../ 越界
boolean isPathSafe(Path base, Path target);

// 获取项目结构（文件树）
List<FileNode> getProjectTree(String relativePath);

// 读取项目规则文件
Optional<String> getProjectRules();
```

### 规则文件
按优先级读取：
1. `JCHATMIND.md`
2. `CLAUDE.md`
3. `AGENTS.md`

---

## 3. ProjectRulesService

### 职责
- 读取工作区根目录的项目规则文件
- 合并到 Agent 系统提示词

### 规则合并
```java
String buildSystemPrompt(AgentConfig config) {
    String basePrompt = config.getSystemPrompt();
    Optional<String> rules = projectRulesService.getRules();
    
    if (rules.isPresent()) {
        return basePrompt + "\n\n## 项目规则\n" + rules.get();
    }
    return basePrompt;
}
```

### 字符预算
- 默认最大 2000 字符（可配置 `coding.project-rules.max-chars`）
- 超出时截断并追加 `[项目规则已截断]`

---

## 4. CodingApprovalService（扩展）

### 复用现有审批流
- 使用现有 `CodingApprovalService` 框架
- 新增审批类型：`MAVEN_COMMAND`

### 审批消息格式
```json
{
  "type": "CODING_APPROVAL_REQUIRED",
  "taskId": "uuid",
  "action": "MAVEN_COMMAND",
  "detail": "mvn test",
  "workspace": "/path/to/workspace",
  "timestamp": "2026-06-01T10:00:00Z"
}
```

### 审批操作
- **approve**：执行命令，返回结果
- **reject**：终止任务，记录拒绝原因

---

## 5. CodingTaskService（新增）

### 职责
管理 Coding 任务生命周期。

### 状态机
```
PENDING → RUNNING → COMPLETED
                ↘ FAILED
                ↘ TIMEOUT
                ↘ WAITING_APPROVAL
```

### 实体
```sql
CREATE TABLE t_coding_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    status VARCHAR(20),           -- PENDING/RUNNING/COMPLETED/FAILED/TIMEOUT/WAITING_APPROVAL
    workspace_path VARCHAR(500),
    started_at TIMESTAMPTZ DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    command VARCHAR(200),         -- 当前执行的命令
    result_summary TEXT,          -- LLM 生成的总结
    metadata JSONB                -- 工具调用统计等
);

CREATE INDEX idx_coding_task_session ON t_coding_task(session_id);
CREATE INDEX idx_coding_task_status ON t_coding_task(status);
```

### 核心方法
```java
String createTask(UUID sessionId, UUID agentId);
void updateStatus(String taskId, TaskStatus status);
void completeTask(String taskId, String summary);
CodingTask getTask(String taskId);
```

---

## 6. Coding Agent 配置

### 创建专用 Agent
```json
{
  "name": "Coding Assistant",
  "model": "deepseek-chat",
  "systemPrompt": "你是一个专业的 Java 开发者，擅长编写高质量的 Java 代码...",
  "allowedTools": [
    "mcp_client",           // MCP filesystem 工具
    "knowledge",            // 知识库检索
    "maven_command"         // Maven 命令执行
  ],
  "chatOptions": {
    "temperature": 0.7,
    "maxTokens": 4096
  },
  "codingConfig": {
    "workspaceRoot": "/path/to/workspace",
    "approvalEnabled": true,
    "projectRulesEnabled": true,
    "maxSteps": 35
  }
}
```

### 工具映射
| 配置名 | 实际工具类 |
|--------|-----------|
| `mcp_client` | MCP filesystem（已有） |
| `knowledge` | KnowledgeTool（已有） |
| `maven_command` | CodingRunTool（新增） |

---

## 7. SSE 消息类型

### 新增消息类型
```java
public class CodingSseMessages {
    
    // 任务开始
    record CodingStarted(String taskId, String workspace) {}
    
    // 审批请求
    record ApprovalRequired(
        String taskId,
        String actionType,
        String detail,
        String workspace
    ) {}
    
    // 命令执行结果
    record CommandOutput(
        String taskId,
        String command,
        int exitCode,
        String output
    ) {}
    
    // 任务完成
    record CodingCompleted(
        String taskId,
        String summary,
        int stepsUsed
    ) {}
    
    // 任务失败
    record CodingFailed(String taskId, String reason) {}
}
```

---

## 8. 执行流程

### 完整对话流程
```
1. 用户: "写一个 Calculator 类，支持加减乘除"

2. Controller 接收 → 持久化消息 → 发布 ChatEvent

3. JChatMindFactory 创建 Agent:
   - 加载 Coding Agent 配置
   - 装配 MCP filesystem 工具
   - 装配 CodingRunTool
   - 注入 ProjectRules

4. JChatMind.run() Think-Execute 循环:
   
   Think 1: 需要创建 Calculator.java
   Execute: MCP filesystem writeFile
   → SSE: AI_GENERATED_CONTENT
   
   Think 2: 需要写测试类
   Execute: MCP filesystem writeFile  
   → SSE: AI_GENERATED_CONTENT
   
   Think 3: 需要运行测试验证
   Execute: CodingRunTool.runMavenCommand("mvn test")
   → 需要审批
   → SSE: APPROVAL_REQUIRED
   
5. 用户审批通过
   
6. 执行 mvn test
   → SSE: COMMAND_OUTPUT
   
7. 测试通过
   → SSE: CODING_COMPLETED
   → 结束
```

---

## 9. 前端交互

### 审批弹窗
```tsx
interface ApprovalModalProps {
  visible: boolean;
  action: 'MAVEN_COMMAND';
  detail: string;
  workspace: string;
  onApprove: () => void;
  onReject: () => void;
}
```

### 消息展示
```
用户: 写一个 Calculator 类

AI: 我来帮你创建一个 Calculator 类...
   [创建 Calculator.java]
   [创建 CalculatorTest.java]
   [运行测试...]
   
   ⚠️ 需要审批
   
   ┌─────────────────────────────┐
   │  执行 Maven 命令             │
   │  mvn test                    │
   │                              │
   │  [批准]  [拒绝]              │
   └─────────────────────────────┘

用户: [批准]

AI: ✓ 测试通过 4/4
   
   ┌─────────────────────────────┐
   │  任务完成                     │
   │  • Calculator.java ✓         │
   │  • CalculatorTest.java ✓     │
   │  • 测试通过 4/4              │
   └─────────────────────────────┘
```

---

## 10. 配置项

```yaml
# application.yaml

coding:
  workspace:
    root: /path/to/workspace  # 工作区根目录
  agent:
    max-loop-steps: 35         # 最大循环步数
    memory-window: 80          # 保留消息数
  approval:
    enabled: true              # 启用审批
  git:
    enabled: true
    auto-detect: true          # 检测 .git 自动启用
  project-rules:
    enabled: true
    files:                     # 按优先级
      - JCHATMIND.md
      - CLAUDE.md
      - AGENTS.md
    max-chars: 2000            # 最大字符数
  maven:
    whitelist:                # 白名单命令
      - mvn compile
      - mvn test
      - mvn test -Dtest={pattern}
      - mvn package -DskipTests
      - mvn clean compile
      - mvn clean test
    timeout-seconds: 300        # 5 分钟超时
    output-max-chars: 2000     # 输出限制
```

---

## 11. 实现顺序

1. **CodingWorkspaceService** - 基础，路径校验
2. **CodingRunTool** - 核心工具
3. **ProjectRulesService** - 规则注入
4. **CodingTaskService** - 任务管理
5. **CodingApprovalService 扩展** - 审批流
6. **SSE 消息扩展** - 消息类型
7. **Controller 适配** - Coding Agent 接口
8. **前端交互** - 审批弹窗

---

## 注意事项

1. **复用优先**：MCP filesystem 工具已有，不重复实现文件读写
2. **审批可控**：可配置 `approval.enabled` 快速关闭
3. **安全第一**：白名单 + 路径锁定 + 超时控制
4. **简洁实现**：先跑通核心流程，再优化细节

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
