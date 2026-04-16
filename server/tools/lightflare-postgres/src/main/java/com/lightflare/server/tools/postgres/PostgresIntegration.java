package com.lightflare.server.tools.postgres;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PostgresIntegration implements Integration {

    private final PostgresProperties properties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("postgres")
                .displayName("PostgreSQL")
                .description("PostgreSQL database integration for schema discovery and querying.")
                .enabled(properties.isEnabled())
                .build();
    }
}
