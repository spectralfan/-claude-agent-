package com.kama.jchatmind.mcp.bootstrap;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 在 Spring 容器刷新前拉起 mcp-proxy，避免 Spring AI SSE 客户端 ConnectException。
 */
public class McpProxyApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();
        if (!env.getProperty("spring.ai.mcp.client.enabled", Boolean.class, false)) {
            return;
        }
        if (!env.getProperty("mcp.proxy.auto-start", Boolean.class, false)) {
            return;
        }
        McpProxyRuntime.ensureStarted(McpProxyRuntime.configFrom(env));
    }
}
