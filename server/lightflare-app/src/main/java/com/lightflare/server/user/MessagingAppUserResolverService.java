package com.lightflare.server.user;

import com.lightflare.server.messaging.MessagingAppUserIdentityProvider;
import com.lightflare.server.messaging.MessagingAppUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@ConditionalOnBean(MessagingAppUserIdentityProvider.class)
@RequiredArgsConstructor
public class MessagingAppUserResolverService implements MessagingAppUserResolver {

    private final MessagingAppUserIdentityProvider messagingAppUserIdentityProvider;
    private final AppUserIdentityService appUserIdentityService;
    private final AppUserRepository appUserRepository;

    @Override
    public String provider() {
        return messagingAppUserIdentityProvider.provider();
    }

    @Override
    public Optional<String> resolveAppUserId(String externalUserId) {
        return messagingAppUserIdentityProvider.normalizeExternalUserId(externalUserId)
                .flatMap(normalizedExternalUserId ->
                        appUserIdentityService.resolveAppUserId(provider(), normalizedExternalUserId)
                                .filter(this::isExistingAppUser)
                );
    }

    private boolean isExistingAppUser(String appUserId) {
        boolean exists = appUserRepository.findById(appUserId).isPresent();
        if (!exists) {
            log.warn("Ignoring stale messaging identity mapping provider={}, appUserId={} because app_user does not exist",
                    provider(),
                    appUserId);
        }
        return exists;
    }
}
