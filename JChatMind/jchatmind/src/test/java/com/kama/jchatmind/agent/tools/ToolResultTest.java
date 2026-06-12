package com.kama.jchatmind.agent.tools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ToolResultTest {
    @Test void ok_shouldCreateSuccessResult() {
        ToolResult r = ToolResult.ok("done");
        assertEquals("done", r.getContent());
        assertFalse(r.isError());
        assertNull(r.getErrorType());
    }
    @Test void error_shouldCreateErrorResult() {
        ToolResult r = ToolResult.error("failed");
        assertEquals("failed", r.getContent());
        assertTrue(r.isError());
        assertEquals("runtime_error", r.getErrorType());
    }
    @Test void error_withCustomType_shouldUseIt() {
        ToolResult r = ToolResult.error("not found", "not_found");
        assertTrue(r.isError());
        assertEquals("not_found", r.getErrorType());
    }
    @Test void nullContent_shouldBeAllowed() {
        ToolResult r = new ToolResult(null);
        assertNull(r.getContent());
        assertFalse(r.isError());
    }
    @Test void constructor_withErrorFlags() {
        ToolResult r = new ToolResult("msg", true, "timeout");
        assertTrue(r.isError());
        assertEquals("timeout", r.getErrorType());
    }
}