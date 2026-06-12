package com.kama.jchatmind.agent.tools.coding;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GitToolsTest {
    @Test void getName_shouldReturnGitTool() {
        assertEquals("git_tool", new GitTools(null, null, null).getName());
    }
    @Test void getDescription_shouldNotBeEmpty() {
        assertNotNull(new GitTools(null, null, null).getDescription());
        assertFalse(new GitTools(null, null, null).getDescription().isEmpty());
    }
    @Test void gitStatus_noContext_shouldReturnError() {
        GitTools t = new GitTools(null, null, null);
        assertTrue(t.gitStatus().contains("错误"));
    }
    @Test void gitDiff_noContext_shouldReturnError() {
        GitTools t = new GitTools(null, null, null);
        assertTrue(t.gitDiff().contains("错误"));
    }
    @Test void gitLog_noContext_shouldReturnError() {
        GitTools t = new GitTools(null, null, null);
        assertTrue(t.gitLog(5).contains("错误"));
    }
    @Test void gitCommit_noContext_shouldReturnError() {
        GitTools t = new GitTools(null, null, null);
        assertTrue(t.gitCommit("msg").contains("错误"));
    }
    @Test void gitCommit_emptyMessage_shouldReturnError() {
        GitTools t = new GitTools(null, null, null);
        assertTrue(t.gitCommit("").contains("不能为空"));
    }
}