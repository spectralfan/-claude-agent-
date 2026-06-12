package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpShellCommandPolicyTest {

    private final McpShellCommandPolicy policy = new McpShellCommandPolicy(
            new ObjectMapper(), new McpProperties());

    @Test
    void rejectReason_shouldNotBlockNodeEval() {
        assertThat(policy.rejectReason("{\"command\":\"node -e console.log(1)\"}"))
                .isEmpty();
    }

    @Test
    void rejectReason_shouldNotBlockHttpServerOn8080() {
        assertThat(policy.rejectReason("http-server -p 8080")).isEmpty();
    }

    @Test
    void rejectReason_shouldAllowNodeCheck() {
        assertThat(policy.rejectReason("{\"command\":\"node --check js/game.js\"}"))
                .isEmpty();
    }

    @Test
    void rejectReason_shouldAllowNpmTest() {
        assertThat(policy.rejectReason("npm test")).isEmpty();
    }

    @Test
    void rejectReason_shouldNotBlockNodeCheckHtml() {
        assertThat(policy.rejectReason("node --check tank-battle.html")).isEmpty();
    }

    @Test
    void rejectReason_shouldNotBlockCmdPipeline() {
        assertThat(policy.rejectReason("type a.html | find </script> >nul")).isEmpty();
    }

    @Test
    void rejectReason_disabledPolicy_shouldPassThrough() {
        McpProperties props = new McpProperties();
        props.getShell().setPolicyEnabled(false);
        McpShellCommandPolicy disabled = new McpShellCommandPolicy(new ObjectMapper(), props);
        assertThat(disabled.rejectReason("node -e bad")).isEmpty();
    }
}