package com.lightflare.server.llmproviders.openai;

import com.lightflare.server.llmproviders.core.LlmProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAIProperties.class)
@ConditionalOnProperty(prefix = "lightflare.llm", name = "provider", havingValue = "openai", matchIfMissing = true)
@ConditionalOnProperty(prefix = "lightflare.llm.openai", name = "enabled", havingValue = "true")
public class OpenAIConfig {

    @Bean
    public RestClient openAIRestClient(OpenAIProperties openAIProperties) {
        String apiKey = openAIProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Missing OpenAI API key: configure lightflare.llm.openai.api-key");
        }
        return RestClient.builder()
                .baseUrl(openAIProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Bean
    public OpenAIService openAIService(
            @Qualifier("openAIRestClient") RestClient openAIRestClient,
            LlmProperties llmProperties,
            OpenAIProperties openAIProperties
    ) {
        return new OpenAIService(
                openAIRestClient,
                openAIProperties.getModel(),
                openAIProperties.getEmbeddingModel(),
                openAIProperties.getEmbeddingDimensions(),
                openAIProperties.getReasoningEffort(),
                llmProperties.getMaxOutputTokens()
        );
    }
}
