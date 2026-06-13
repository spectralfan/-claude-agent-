# Event 事件系统重构设计文档

> 将现有 4 个 POJO 事件升级为 Jackson 多态事件模型，对标 KamaClaude 的 23 种事件体系。

## 现状

当前有 4 个事件类：
- StepStartedEvent
- StepFinishedEvent  
- ToolCalledEvent
- ToolResultEvent

问题：
1. 无 type 字段，序列化为 JSONL 后无法反序列化回具体类型
2. EventBus 接收 Object，无类型安全
3. 事件种类太少（缺 Run 生命周期、LLM 用量、权限等）

## 目标

### 事件基类

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(StepStartedEvent.class),
    @Type(StepFinishedEvent.class),
    ...
})
public abstract class Event {
    public abstract String getType();
    public abstract String getTs();
}
```

### 改造 4 个现有事件

每个事件加 String type 字段（"step.started" 等），从继承 Object 改为继承 Event。

### 新增 8 个事件

| 事件 | type | 来源参考 |
|------|------|---------|
| RunStartedEvent | "run.started" | KamaClaude RunStartedEvent |
| RunFinishedEvent | "run.finished" | KamaClaude RunFinishedEvent |
| LlmUsageEvent | "llm.usage" | KamaClaude LlmUsageEvent |
| LlmModelSelectedEvent | "llm.model_selected" | KamaClaude LlmModelSelectedEvent |
| PermissionRequestedEvent | "permission.requested" | KamaClaude PermissionRequestedEvent |
| PermissionGrantedEvent | "permission.granted" | KamaClaude PermissionGrantedEvent |
| PermissionDeniedEvent | "permission.denied" | KamaClaude PermissionDeniedEvent |
| ContextCompactedEvent | "context.compacted" | KamaClaude ContextCompactedEvent |

### EventBus 类型安全

```java
@Component
public class EventBus {
    private final List<Consumer<? super Event>> handlers;
    public void subscribe(Consumer<? super Event> handler) { ... }
    public void publish(Event event) { ... }
}
```

### JSONL 写入

```json
{"type":"run.started","runId":"xxx","goal":"yyy","ts":"2026-06-12T10:00:00Z"}
```

通过 @JsonTypeInfo 序列化时自动带 type，反序列化时自动按 type 匹配子类。