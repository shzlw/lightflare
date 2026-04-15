package com.lightflare.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableScheduling
@EnableAsync
public class ExecutorConfig {

    @Bean(name = "scheduledTaskExecutor")
    public Executor scheduledTaskExecutor(SchedulerProperties schedulerProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("scheduled-task-");
        executor.setCorePoolSize(schedulerProperties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(schedulerProperties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(schedulerProperties.getExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean(name = "memoryEmbeddingExecutor")
    public Executor memoryEmbeddingExecutor(MemoryProperties memoryProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("memory-embedding-");
        executor.setCorePoolSize(memoryProperties.getEmbeddingExecutorCorePoolSize());
        executor.setMaxPoolSize(memoryProperties.getEmbeddingExecutorMaxPoolSize());
        executor.setQueueCapacity(memoryProperties.getEmbeddingExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean(name = "dagPlanTaskExecutor")
    public Executor dagPlanTaskExecutor(AgentProperties agentProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("dag-plan-");
        executor.setCorePoolSize(agentProperties.getExecutionExecutorCorePoolSize());
        executor.setMaxPoolSize(agentProperties.getExecutionExecutorMaxPoolSize());
        executor.setQueueCapacity(agentProperties.getExecutionExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }

    @Bean(name = "chatStreamExecutor")
    public Executor chatStreamExecutor(ChatProperties chatProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("chat-stream-");
        executor.setCorePoolSize(chatProperties.getStreamExecutorCorePoolSize());
        executor.setMaxPoolSize(chatProperties.getStreamExecutorMaxPoolSize());
        executor.setQueueCapacity(chatProperties.getStreamExecutorQueueCapacity());
        executor.initialize();
        return executor;
    }
}
