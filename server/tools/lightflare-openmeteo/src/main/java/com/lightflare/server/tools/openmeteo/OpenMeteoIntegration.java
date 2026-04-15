package com.lightflare.server.tools.openmeteo;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenMeteoIntegration implements Integration {

    private final OpenMeteoProperties openMeteoProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("openmeteo")
                .displayName("Open-Meteo")
                .description("Weather forecast and geocoding tools powered by Open-Meteo.")
                .enabled(openMeteoProperties.enabled())
                .build();
    }
}
