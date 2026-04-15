package com.lightflare.server.llmproviders.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.llm")
public class LlmProperties {

    private String provider = "openai";

    private Integer maxOutputTokens = 4096;
}
