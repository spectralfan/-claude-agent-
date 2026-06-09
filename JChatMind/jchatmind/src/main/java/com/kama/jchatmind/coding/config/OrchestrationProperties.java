package com.kama.jchatmind.coding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "coding.orchestration")
public class OrchestrationProperties {

    /** 最大嵌套深度（Scheduler=0，子任务=1） */
    private int maxDepth = 1;
    /** 同会话最大并行 RUNNING 任务数 */
    private int maxConcurrency = 4;
    /** Worker 完成后自动创建 Reviewer 任务 */
    private boolean autoReview = true;
    /** Reviewer 任务 depends_on 对应 Worker */
    private boolean autoReviewDepends = true;
    /** Dispatcher 扫描间隔 ms */
    private long dispatchIntervalMs = 2000;
    /** read_workspace_file 最大字节 */
    private int readMaxBytes = 512 * 1024;
    /** run_workspace_shell 输出最大字符 */
    private int shellOutputMaxChars = 64 * 1024;
    /** list_workspace_dir 默认深度 */
    private int listDirDefaultDepth = 2;
    /** list_workspace_dir 最大深度 */
    private int listDirMaxDepth = 5;
}
