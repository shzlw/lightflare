package com.lightflare.server.integration;

public record IntegrationResponse(
        String id,
        String displayName,
        String description,
        boolean enabled
) {
}
