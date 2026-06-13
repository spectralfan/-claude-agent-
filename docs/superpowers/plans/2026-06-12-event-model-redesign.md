# 执行计划：Event 事件系统重构

## 已完成
- [x] 设计文档: docs/superpowers/specs/2026-06-12-event-model-redesign.md
- [x] Event.java — 基类，@JsonTypeInfo + @JsonSubTypes 多态
- [x] StepStartedEvent.java — 改继承 Event，加 type 字段
- [x] StepFinishedEvent.java — 同上
- [x] ToolCalledEvent.java — 同上
- [x] ToolResultEvent.java — 同上
- [x] RunStartedEvent.java — 新增
- [x] RunFinishedEvent.java — 新增
- [x] LlmUsageEvent.java — 新增
- [x] PermissionRequestedEvent.java — 新增
- [x] PermissionGrantedEvent.java — 新增
- [x] PermissionDeniedEvent.java — 新增
- [x] ContextCompactedEvent.java — 新增
- [x] EventBus.java — Consumer<? super Event> 类型安全
- [x] EventWriter.java — JSONL 写入带 type 字段
- [x] EventSerializationTest — 10 个测试全部通过
- [x] 全量测试 — 20+ 测试类全部通过
- [x] Compile — 无错误