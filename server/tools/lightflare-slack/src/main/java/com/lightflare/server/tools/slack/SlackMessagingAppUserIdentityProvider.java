package com.lightflare.server.tools.slack;

import com.lightflare.server.messaging.MessagingAppUserIdentityProvider;
import org.springframework.util.StringUtils;

import java.util.Optional;

public class SlackMessagingAppUserIdentityProvider implements MessagingAppUserIdentityProvider {

    public static final String PROVIDER = "slack";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Optional<String> normalizeExternalUserId(String externalUserId) {
        if (!StringUtils.hasText(externalUserId)) {
            return Optional.empty();
        }
        return Optional.of(externalUserId.trim().toLowerCase());
    }
}
