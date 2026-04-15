package com.lightflare.server.llmproviders.ollama;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
@ConditionalOnProperty(prefix = "lightflare.llm", name = "provider", havingValue = "ollama")
@ConditionalOnProperty(prefix = "lightflare.llm.ollama", name = "enabled", havingValue = "true")
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties ollamaProperties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(ollamaProperties.getBaseUrl());
        if (StringUtils.hasText(ollamaProperties.getApiKey())) {
            builder.defaultHeaders(headers -> headers.setBearerAuth(ollamaProperties.getApiKey()));
        }
        return builder.build();
    }

    @Bean
    public OllamaService ollamaService(
            @Qualifier("ollamaRestClient") RestClient ollamaRestClient,
            OllamaProperties ollamaProperties
    ) {
        if (!StringUtils.hasText(ollamaProperties.getModel())) {
            throw new IllegalStateException("Missing Ollama model: configure lightflare.llm.ollama.model");
        }
        return new OllamaService(
                ollamaRestClient,
                ollamaProperties.getModel(),
                ollamaProperties.getEmbeddingModel(),
                ollamaProperties.getEmbeddingDimensions()
        );
    }
}
