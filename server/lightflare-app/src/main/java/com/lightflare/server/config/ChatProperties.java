package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.chat")
public class ChatProperties {

    private int streamExecutorCorePoolSize = 4;

    private int streamExecutorMaxPoolSize = 8;

    private int streamExecutorQueueCapacity = 100;
}
