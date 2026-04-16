package com.lightflare.server.tools.email;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailIntegration implements Integration {

    private final EmailProperties emailProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("email")
                .displayName("Email")
                .description("Email sending integration for sending messages and notifications.")
                .enabled(emailProperties.isEnabled())
                .build();
    }
}
