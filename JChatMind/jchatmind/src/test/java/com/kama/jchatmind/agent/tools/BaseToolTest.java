package com.kama.jchatmind.agent.tools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Map;

class BaseToolTest {
    @Test void defaultExecute_shouldReturnError() {
        BaseTool t = new BaseTool() {
            public String getName() { return "test"; }
            public String getDescription() { return "test desc"; }
        };
        ToolResult r = t.execute(Map.of());
        assertTrue(r.isError());
        assertTrue(r.getContent().contains("Not implemented"));
    }
    @Test void defaultGetType_shouldBeOptional() {
        BaseTool t = new BaseTool() {
            public String getName() { return "t"; }
            public String getDescription() { return "d"; }
        };
        assertEquals(ToolType.OPTIONAL, t.getType());
    }
    @Test void customTool_shouldOverrideExecute() {
        BaseTool t = new BaseTool() {
            public String getName() { return "hello"; }
            public String getDescription() { return ""; }
            public ToolResult execute(Map<String, Object> p) { return ToolResult.ok("custom"); }
        };
        assertEquals("custom", t.execute(Map.of()).getContent());
        assertFalse(t.execute(Map.of()).isError());
    }
}