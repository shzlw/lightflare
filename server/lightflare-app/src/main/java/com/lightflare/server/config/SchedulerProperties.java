package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.scheduler")
public class SchedulerProperties {

    private long pollIntervalMs = 60000;

    private int batchSize = 20;

    private long leaseDurationSeconds = 300;

    private int executorCorePoolSize = 4;

    private int executorMaxPoolSize = 8;

    private int executorQueueCapacity = 100;
}
