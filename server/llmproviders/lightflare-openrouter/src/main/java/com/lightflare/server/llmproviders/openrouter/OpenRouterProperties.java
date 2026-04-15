package com.lightflare.server.llmproviders.openrouter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lightflare.llm.openrouter")
public class OpenRouterProperties {

    private boolean enabled = false;

    private String baseUrl = "https://openrouter.ai/api/v1";

    private String apiKey;

    private String model = "openai/gpt-4.1-nano";

    private String embeddingModel = "openai/text-embedding-3-small";

    private Integer maxCompletionTokens;

    private Double temperature;

    private Double topP;

    private Integer seed;

    private String user;

    private Map<String, Object> provider;

    private Integer embeddingDimensions;

    private String embeddingEncodingFormat;

    private String embeddingInputType;

    private Map<String, Object> embeddingProvider;

    private String httpReferer;

    private String title;

    private String categories;
}
