package com.kama.jchatmind.agent.tools;

import com.kama.jchatmind.agent.config.AgentToolResultProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultCompactorTest {

    private ToolResultCompactor compactor;

    @BeforeEach
    void setUp() {
        AgentToolResultProperties properties = new AgentToolResultProperties();
        properties.setEnabled(true);
        properties.setDefaultMaxChars(100);
        properties.setHeadTailMaxChars(80);
        compactor = new ToolResultCompactor(properties);
    }

    @Test
    void compact_verifyTool_exit0_shouldReturnOk() {
        String result = compactor.compact(
                "verify_coding_file",
                "exit code: 0\nOK: pom.xml 存在，大小 1200 字节");
        assertEquals("OK", result);
    }

    @Test
    void compact_verifyTool_exit1_shouldReturnFailed() {
        String result = compactor.compact(
                "check_js_syntax",
                "exit code: 1\n语法错误: unexpected token");
        assertEquals("FAILED: 语法错误: unexpected token", result);
    }

    @Test
    void compact_writeFile_success_shouldReturnOk() {
        assertEquals("OK", compactor.compact("write_coding_file", "成功写入: src/App.java"));
    }

    @Test
    void compact_shell_longOutput_shouldHeadTail() {
        String longOut = "exit code: 0\n" + "x".repeat(500);
        String result = compactor.compact("run_terminal_cmd", longOut);
        assertTrue(result.contains("输出已截断"));
        assertTrue(result.length() < longOut.length());
    }

    @Test
    void compact_readFile_long_shouldMaxChars() {
        String longContent = "a".repeat(200);
        String result = compactor.compact("read_coding_file", longContent);
        assertTrue(result.contains("[truncated, total=200]"));
    }

    @Test
    void compact_disabled_shouldReturnOriginal() {
        AgentToolResultProperties properties = new AgentToolResultProperties();
        properties.setEnabled(false);
        ToolResultCompactor off = new ToolResultCompactor(properties);
        String raw = "exit code: 0\nlots of detail";
        assertEquals(raw, off.compact("verify_coding_file", raw));
    }
}
