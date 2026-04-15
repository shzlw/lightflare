package com.lightflare.server.tools.slack;

import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackIntegration implements Integration {

    private final SlackProperties slackProperties;

    @Override
    public IntegrationDefinition definition() {
        return IntegrationDefinition.builder()
                .id("slack")
                .displayName("Slack")
                .description("Slack messaging and event-driven collaboration integrations.")
                .enabled(slackProperties.isEnabled())
                .build();
    }
}
