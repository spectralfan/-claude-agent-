package com.kama.jchatmind.coding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "coding.subagent")
public class CodingSubagentProperties {

    private boolean enabled = true;
    /** 子 Agent 使用的 worker 预设名（按 agent.name 查找，默认 Claude Code Coding Agent） */
    private String workerAgentName = "Claude Code Coding Agent";
    private int maxLoopSteps = 35;
}
