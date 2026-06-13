package com.kama.jchatmind.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class EventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test void stepStarted_serialization_includesType() throws Exception {
        StepStartedEvent e = new StepStartedEvent("run-1", 1, Instant.now().toString());
        String json = mapper.writeValueAsString(e);
        assertTrue(json.contains("\"type\":\"step.started\""), "should have type field: " + json);
    }

    @Test void stepStarted_deserialization_restoresType() throws Exception {
        String json = "{\"type\":\"step.started\",\"runId\":\"r1\",\"step\":2,\"ts\":\"2026-01-01T00:00:00Z\"}";
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(StepStartedEvent.class, event);
        StepStartedEvent e = (StepStartedEvent) event;
        assertEquals("r1", e.getRunId());
        assertEquals(2, e.getStep());
    }

    @Test void runStarted_serialization_includesType() throws Exception {
        RunStartedEvent e = new RunStartedEvent("run-1", "my goal", Instant.now().toString());
        String json = mapper.writeValueAsString(e);
        assertTrue(json.contains("\"type\":\"run.started\""));
    }

    @Test void runStarted_deserialization_restoresType() throws Exception {
        String json = "{\"type\":\"run.started\",\"runId\":\"r1\",\"goal\":\"my goal\",\"ts\":\"2026-01-01T00:00:00Z\"}";
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(RunStartedEvent.class, event);
        RunStartedEvent e = (RunStartedEvent) event;
        assertEquals("my goal", e.getGoal());
    }

    @Test void runFinished_roundtrip() throws Exception {
        RunFinishedEvent e = new RunFinishedEvent("r1", "success", null, 5, "2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(RunFinishedEvent.class, event);
        RunFinishedEvent r = (RunFinishedEvent) event;
        assertEquals("success", r.getStatus());
        assertEquals(5, r.getSteps());
    }

    @Test void toolCalled_roundtrip() throws Exception {
        ToolCalledEvent e = new ToolCalledEvent("r1", "bash", "{\"cmd\":\"ls\"}", 1);
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(ToolCalledEvent.class, event);
        assertEquals("bash", ((ToolCalledEvent)event).getToolName());
    }

    @Test void toolResult_roundtrip() throws Exception {
        ToolResultEvent e = new ToolResultEvent("r1", "bash", "ok", false, 1);
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(ToolResultEvent.class, event);
        assertFalse(((ToolResultEvent)event).isError());
    }

    @Test void llmUsage_roundtrip() throws Exception {
        LlmUsageEvent e = new LlmUsageEvent("r1", 100, 50, 0.75, "2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(LlmUsageEvent.class, event);
        assertEquals(100, ((LlmUsageEvent)event).getInputTokens());
        assertEquals(0.75, ((LlmUsageEvent)event).getContextPct(), 0.001);
    }

    @Test void permission_roundtrip() throws Exception {
        PermissionRequestedEvent e = new PermissionRequestedEvent("r1", "bash", "ls", "s1", "2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(PermissionRequestedEvent.class, event);
        assertEquals("bash", ((PermissionRequestedEvent)event).getToolName());
    }

    @Test void contextCompacted_roundtrip() throws Exception {
        ContextCompactedEvent e = new ContextCompactedEvent("s1", "r1", 5000, 800, "2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(e);
        Event event = mapper.readValue(json, Event.class);
        assertInstanceOf(ContextCompactedEvent.class, event);
        assertEquals(5000, ((ContextCompactedEvent)event).getOriginalTokens());
    }
}