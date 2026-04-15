package com.lightflare.server.contextsearch;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.context-search")
public class ContextSearchProperties {

    private int minCandidateLimit = 20;

    private int candidateLimitMultiplier = 4;

    private int maxChunksPerDocument = 2;

    private int memoryPageSearchLimit = 500;

    private double vectorWeight = 0.55;

    private double textWeight = 0.30;

    private double recencyWeight = 0.10;

    private double scopeWeight = 0.05;
}
