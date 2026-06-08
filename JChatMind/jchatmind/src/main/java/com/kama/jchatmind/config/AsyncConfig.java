package com.kama.jchatmind.config;

import com.kama.jchatmind.coding.config.CodingSubagentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AsyncConfig {

    private final CodingSubagentProperties codingSubagentProperties;

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-event-");
        executor.initialize();
        return executor;
    }

    /** Worker 子任务与 Orchestrator 自动继续专用，避免与邮件/Memory 等短任务抢池 */
    @Bean(name = "codingExecutor")
    public Executor codingExecutor() {
        int pool = Math.max(2, codingSubagentProperties.getPoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool);
        executor.setMaxPoolSize(pool * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("coding-agent-");
        executor.initialize();
        return executor;
    }
}
