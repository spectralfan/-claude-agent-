package com.kama.jchatmind.coding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "coding.subagent")
public class CodingSubagentProperties {

    private int maxLoopSteps = 35;
    /** 子任务结束（成功/失败）后自动唤醒 Orchestrator 继续编排 */
    private boolean autoContinue = true;
    /** 单会话自动继续次数上限，防止死循环 */
    private int maxAutoContinuations = 30;
    /** codingExecutor 核心线程数（子任务 + 编排自动继续） */
    private int poolSize = 4;
}
