# Context 上下文压缩设计文档

> 参考 KamaClaude 的三层压缩架构，为 JChatMindv2 实现 AI 摘要压缩。

## 架构

```
L1: ToolResultCompactor (已有)
  tool_result > 8000 字 → 截断为前 4000 + 省略标记

L2: ContextCompactor (新增)
  contextPct >= 80% →
    1. 格式化 messages 为纯文本
    2. LLM 生成结构化摘要 (6段)
    3. 替换 messages 为 [摘要, ACK]
    4. 写入 summary_<ts>.md
    5. 发布 ContextCompactedEvent

L3: AgentLoop 触发 (改造)
  每步循环末尾检测 contextPct >= threshold
```

## 新增文件

| 文件 | 说明 |
|------|------|
| ContextCompactor.java | AI 摘要压缩器 |
| ContextCompactorTest.java | 测试 |
| CompactProperties.java | 配置 (threshold/model) |