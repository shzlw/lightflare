package com.lightflare.server.tools.bravesearch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BraveSearchProperties.class)
@ConditionalOnProperty(prefix = "lightflare.tools.brave-search", name = "enabled", havingValue = "true")
public class BraveSearchConfig {

    @Bean
    RestClient braveSearchRestClient(BraveSearchProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("Missing Brave Search API key: configure lightflare.tools.brave-search.api-key");
        }
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader("X-Subscription-Token", properties.apiKey())
                .build();
    }

    @Bean
    BraveSearchService braveSearchService(
            @Qualifier("braveSearchRestClient") RestClient braveSearchRestClient,
            BraveSearchProperties properties
    ) {
        return new BraveSearchService(braveSearchRestClient, properties);
    }

    @Bean
    WebSearchTool webSearchTool(BraveSearchService braveSearchService) {
        return new WebSearchTool(braveSearchService);
    }
}
