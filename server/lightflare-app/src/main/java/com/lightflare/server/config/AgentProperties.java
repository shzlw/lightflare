package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.agent")
public class AgentProperties {

    private int maxStepAttempts = 3;
    private int maxParallelSteps = 4;
    private int maxExecutionWaves = 8;
    private int maxReplans = 2;
    private int maxResponseResolutionRounds = 2;
    private int executionExecutorCorePoolSize = 4;
    private int executionExecutorMaxPoolSize = 8;
    private int executionExecutorQueueCapacity = 100;
}
