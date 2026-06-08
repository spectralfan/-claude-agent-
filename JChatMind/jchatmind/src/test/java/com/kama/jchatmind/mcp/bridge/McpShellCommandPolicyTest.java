package com.kama.jchatmind.mcp.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpShellCommandPolicyTest {

    private final McpShellCommandPolicy policy = new McpShellCommandPolicy(
            new ObjectMapper(), new McpProperties());

    @Test
    void rejectReason_shouldBlockNodeEval() {
        assertThat(policy.rejectReason("{\"command\":\"node -e \\\"console.log(1)\\\"\"}"))
                .isPresent()
                .get()
                .asString()
                .contains("策略拦截")
                .contains("check_js_syntax");
    }

    @Test
    void rejectReason_shouldBlockHttpServerOn8080() {
        assertThat(policy.rejectReason("http-server -p 8080"))
                .isPresent()
                .get()
                .asString()
                .contains("8080");
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
    void rejectReason_shouldBlockNodeCheckHtml() {
        assertThat(policy.rejectReason("node --check tank-battle.html"))
                .isPresent()
                .get()
                .asString()
                .contains("verify_coding_file");
    }

    @Test
    void rejectReason_shouldBlockCmdPipeline() {
        assertThat(policy.rejectReason("type a.html | find \"</script>\" >nul"))
                .isPresent()
                .get()
                .asString()
                .contains("管道");
    }

    @Test
    void rejectReason_disabledPolicy_shouldPassThrough() {
        McpProperties props = new McpProperties();
        props.getShell().setPolicyEnabled(false);
        McpShellCommandPolicy disabled = new McpShellCommandPolicy(new ObjectMapper(), props);

        assertThat(disabled.rejectReason("node -e \"bad\"")).isEmpty();
    }
}
