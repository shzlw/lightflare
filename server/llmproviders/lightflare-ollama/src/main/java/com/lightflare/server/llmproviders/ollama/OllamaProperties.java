package com.lightflare.server.llmproviders.ollama;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.llm.ollama")
public class OllamaProperties {

    private boolean enabled = false;

    private String baseUrl = "http://127.0.0.1:11434";

    private String model;

    private String apiKey;

    private String embeddingModel;

    private int embeddingDimensions = 1536;
}
