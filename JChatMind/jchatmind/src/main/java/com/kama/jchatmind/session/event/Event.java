package com.kama.jchatmind.session.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StepStartedEvent.class, name = "step.started"),
    @JsonSubTypes.Type(value = StepFinishedEvent.class, name = "step.finished"),
    @JsonSubTypes.Type(value = ToolCalledEvent.class, name = "tool.called"),
    @JsonSubTypes.Type(value = ToolResultEvent.class, name = "tool.result"),
    @JsonSubTypes.Type(value = RunStartedEvent.class, name = "run.started"),
    @JsonSubTypes.Type(value = RunFinishedEvent.class, name = "run.finished"),
    @JsonSubTypes.Type(value = LlmUsageEvent.class, name = "llm.usage"),
    @JsonSubTypes.Type(value = PermissionRequestedEvent.class, name = "permission.requested"),
    @JsonSubTypes.Type(value = PermissionGrantedEvent.class, name = "permission.granted"),
    @JsonSubTypes.Type(value = PermissionDeniedEvent.class, name = "permission.denied"),
    @JsonSubTypes.Type(value = ContextCompactedEvent.class, name = "context.compacted"),
    @JsonSubTypes.Type(value = SubagentStartedEvent.class, name = "subagent.started"),
    @JsonSubTypes.Type(value = SubagentFinishedEvent.class, name = "subagent.finished")
})
public abstract class Event {
    public abstract String getType();
    public abstract String getTs();
}