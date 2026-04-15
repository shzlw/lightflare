package com.lightflare.server.tools.httpclient;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpClientIntegration implements Integration {

    private final HttpClientProperties httpClientProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("httpclient")
                .displayName("HTTP Client")
                .description("HTTP request tools for GET, POST, PUT, and DELETE.")
                .enabled(httpClientProperties.enabled())
                .build();
    }
}
