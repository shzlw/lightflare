package com.lightflare.server.tools.bravesearch;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BraveSearchIntegration implements Integration {

    private final BraveSearchProperties braveSearchProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("brave_search")
                .displayName("Brave Search")
                .description("Web search tools powered by Brave Search API.")
                .enabled(braveSearchProperties.enabled())
                .build();
    }
}
