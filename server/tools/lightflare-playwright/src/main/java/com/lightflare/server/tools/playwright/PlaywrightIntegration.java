package com.lightflare.server.tools.playwright;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaywrightIntegration implements Integration {

    private final PlaywrightProperties playwrightProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("playwright")
                .displayName("Playwright")
                .description("Browser automation and rendered web page extraction tools.")
                .enabled(playwrightProperties.isEnabled())
                .build();
    }
}
