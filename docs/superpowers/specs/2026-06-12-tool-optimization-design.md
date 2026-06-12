# MCP 工具优化设计文档

> 统一 Shell 执行 / 新增 Git + read_url 工具 / 工具接口标准化

## 动机

1. Shell 执行路径混乱：6 个别名膨胀 + 硬拦截策略补丁
2. Agent 缺少 Git 版本管理和 Web 资料查阅能力
3. 工具返回值不统一（有的抛异常，有的返回 String）
4. 测试覆盖率不足

## 设计

### Phase 1: Shell 统一
- 规范名: bash，别名保留向后兼容
- 策略从硬拦截改为软 WARN

### Phase 2: Git 工具
- 通过 SandboxCommandRunner 在工作区执行 git
- 4 个子工具: git_status / git_diff / git_log / git_commit

### Phase 3: read_url
- Java HttpClient，15s 超时，8KB 截断

### Phase 4: 标准化
- ToolResult: ok() / error() 工厂方法
- BaseTool: 抽象基类