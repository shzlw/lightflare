package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.memory")
public class MemoryProperties {

    private String uploadDir = "/tmp/lightflare/memory-uploads";

    private int compactionTokenThreshold = 3000;

    private int compactionBatchSize = 5;

    private int sessionMemoryLimit = 50;

    private int similarityResultLimit = 10;

    private int embeddingExecutorCorePoolSize = 2;

    private int embeddingExecutorMaxPoolSize = 4;

    private int embeddingExecutorQueueCapacity = 100;
}
