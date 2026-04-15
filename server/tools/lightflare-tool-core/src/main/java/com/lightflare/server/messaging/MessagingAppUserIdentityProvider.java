package com.lightflare.server.messaging;

import java.util.Optional;

public interface MessagingAppUserIdentityProvider {

    String provider();

    Optional<String> normalizeExternalUserId(String externalUserId);
}
