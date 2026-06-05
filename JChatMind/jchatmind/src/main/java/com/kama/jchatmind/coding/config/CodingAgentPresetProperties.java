package com.kama.jchatmind.coding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "coding.agent-preset")
public class CodingAgentPresetProperties {

    /** 启动时若不存在则自动创建预设 Agent */
    private boolean enabled = true;

    /** classpath 预设 JSON 路径 */
    private String resource = "classpath:coding-agent-preset.json";
}
