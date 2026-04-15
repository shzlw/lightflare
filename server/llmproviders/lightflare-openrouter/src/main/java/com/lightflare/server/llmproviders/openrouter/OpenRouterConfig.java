package com.lightflare.server.llmproviders.openrouter;

import com.lightflare.server.llmproviders.core.LlmProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
@ConditionalOnProperty(prefix = "lightflare.llm", name = "provider", havingValue = "openrouter")
@ConditionalOnProperty(prefix = "lightflare.llm.openrouter", name = "enabled", havingValue = "true")
public class OpenRouterConfig {

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties openRouterProperties) {
        if (!StringUtils.hasText(openRouterProperties.getApiKey())) {
            throw new IllegalStateException("Missing OpenRouter API key: configure lightflare.llm.openrouter.api-key");
        }

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(openRouterProperties.getBaseUrl())
                .defaultHeaders(headers -> {
                    headers.setBearerAuth(openRouterProperties.getApiKey());
                    if (StringUtils.hasText(openRouterProperties.getHttpReferer())) {
                        headers.set("HTTP-Referer", openRouterProperties.getHttpReferer());
                    }
                    if (StringUtils.hasText(openRouterProperties.getTitle())) {
                        headers.set("X-OpenRouter-Title", openRouterProperties.getTitle());
                    }
                    if (StringUtils.hasText(openRouterProperties.getCategories())) {
                        headers.set("X-OpenRouter-Categories", openRouterProperties.getCategories());
                    }
                });
        return builder.build();
    }

    @Bean
    public OpenRouterService openRouterService(
            @Qualifier("openRouterRestClient") RestClient openRouterRestClient,
            LlmProperties llmProperties,
            OpenRouterProperties openRouterProperties
    ) {
        if (!StringUtils.hasText(openRouterProperties.getModel())) {
            throw new IllegalStateException("Missing OpenRouter model: configure lightflare.llm.openrouter.model");
        }
        return new OpenRouterService(
                openRouterRestClient,
                openRouterProperties,
                llmProperties.getMaxOutputTokens()
        );
    }
}
