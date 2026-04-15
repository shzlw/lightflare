package com.lightflare.server.llmproviders.openai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.llm.openai")
public class OpenAIProperties {

    private boolean enabled = false;

    private String model = "gpt-5.4-nano";

    private String embeddingModel = "text-embedding-3-small";

    private String reasoningEffort;

    private String apiKey;

    private String baseUrl = "https://api.openai.com/v1";

    private int embeddingDimensions = 1536;
}
