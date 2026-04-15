package com.lightflare.server.messaging;

import java.util.Optional;

public interface MessagingAppUserResolver {

    String provider();

    Optional<String> resolveAppUserId(String externalUserId);
}
