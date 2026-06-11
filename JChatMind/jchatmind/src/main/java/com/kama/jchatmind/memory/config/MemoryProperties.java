package com.kama.jchatmind.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Memory Hub 配置项，前缀 memory.hub。
 * 项目首个 @ConfigurationProperties 配置类。
 */
@Data
@Component
@ConfigurationProperties(prefix = "memory.hub")
public class MemoryProperties {

    /** 是否启用 Memory Hub（用于与 Think-Execute 主流程 opt-in 集成，阶段 5-6 使用） */
    private boolean enabled = false;

    /** 是否在 loadMemory 时注入 RECENT/ARCHIVE 补充上下文（普通聊天） */
    private boolean supplementalEnabled = true;

    /** 活跃 Coding 任务时是否注入 RECENT/ARCHIVE（同会话恢复长期记忆） */
    private boolean codingSupplementalEnabled = true;

    /** embedding 模型名（复用本地 Ollama 的 bge-m3） */
    private String embeddingModel = "bge-m3";

    /** 向量维度，需与数据库 t_memory_embedding.embedding 一致 */
    private int embeddingDimension = 1024;

    /** WORKING 层滑动窗口大小（最近 N 条） */
    private int workingWindowSize = 20;

    /** 升级到 RECENT 的重要性阈值 */
    private int recentImportanceThreshold = 7;

    /** RECENT 层最大条目数，超出触发整理 */
    private int recentMaxEntries = 50;

    /** RECENT 多久未访问则归档为 ARCHIVE（分钟） */
    private long recentIdleMinutes = 60;

    /** 上下文构建的 token 预算占模型上下文窗口的比例 */
    private double tokenBudgetRatio = 0.7;

    /** 默认模型上下文窗口大小（token），用于预算计算 */
    private int defaultContextWindow = 8192;

    /** 整理任务使用的对话模型 bean 名称 */
    private String consolidationModel = "deepseek-chat";

    /** 是否启用记忆任务的定时轮询执行 */
    private boolean taskPollingEnabled = true;

    /** 任务轮询间隔（毫秒） */
    private long taskPollIntervalMs = 10000;

    /** 每次轮询处理的任务数 */
    private int taskBatchSize = 5;

    /** Ollama 服务地址（bge-m3 向量化，Memory Hub ARCHIVE 与知识库 RAG 共用） */
    private String ollamaBaseUrl = "http://localhost:11434";
}
