package com.lightflare.server.user;

import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppUserIdentityService {

    private final AppUserIdentityRepository appUserIdentityRepository;
    private final AuthService authService;

    public List<AppUserIdentityResponse> listIdentities(String appUserId, HttpServletRequest request) {
        requireManageableUser(appUserId, request);
        return appUserIdentityRepository.findByAppUserId(appUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    public AppUserIdentityResponse createIdentity(String appUserId,
                                                  AppUserIdentityRequest createRequest,
                                                  HttpServletRequest request) {
        requireManageableUser(appUserId, request);
        if (createRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String provider = normalize(createRequest.getProvider(), "provider");
        String externalUserId = normalize(createRequest.getExternalUserId(), "externalUserId");
        ensureUniqueIdentity(provider, externalUserId, null);

        AppUserIdentity identity = new AppUserIdentity();
        identity.setId(UUID.randomUUID().toString());
        identity.setAppUserId(appUserId);
        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setCreatedAt(DateUtils.now());
        identity.setUpdatedAt(identity.getCreatedAt());

        int inserted = appUserIdentityRepository.insert(
                identity.getId(),
                identity.getAppUserId(),
                identity.getProvider(),
                identity.getExternalUserId(),
                identity.getCreatedAt(),
                identity.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one app_user_identity row to be inserted but got " + inserted);
        }

        return appUserIdentityRepository.findById(identity.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("App user identity was inserted but could not be loaded"));
    }

    public void deleteIdentity(String appUserId, String identityId, HttpServletRequest request) {
        requireManageableUser(appUserId, request);
        AppUserIdentity identity = appUserIdentityRepository.findByIdAndAppUserId(identityId, appUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity mapping not found"));
        int deleted = appUserIdentityRepository.deleteByIdValue(identity.getId());
        if (deleted != 1) {
            throw new IllegalStateException("Expected one app_user_identity row to be deleted but got " + deleted);
        }
    }

    public AppUserIdentityResponse updateIdentity(String appUserId,
                                                  String identityId,
                                                  AppUserIdentityRequest updateRequest,
                                                  HttpServletRequest request) {
        requireManageableUser(appUserId, request);
        if (updateRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        AppUserIdentity identity = appUserIdentityRepository.findByIdAndAppUserId(identityId, appUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity mapping not found"));
        String provider = normalize(updateRequest.getProvider(), "provider");
        String externalUserId = normalize(updateRequest.getExternalUserId(), "externalUserId");
        ensureUniqueIdentity(provider, externalUserId, identity.getId());

        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setUpdatedAt(DateUtils.now());

        int updated = appUserIdentityRepository.updateIdentity(
                identity.getId(),
                identity.getProvider(),
                identity.getExternalUserId(),
                identity.getUpdatedAt()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one app_user_identity row to be updated but got " + updated);
        }

        return appUserIdentityRepository.findById(identity.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("App user identity was updated but could not be loaded"));
    }

    public java.util.Optional<String> resolveAppUserId(String provider, String externalUserId) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(externalUserId)) {
            return java.util.Optional.empty();
        }
        return appUserIdentityRepository.findByProviderAndExternalUserId(
                        provider.trim().toLowerCase(Locale.ROOT),
                        externalUserId.trim().toLowerCase(Locale.ROOT)
                )
                .map(AppUserIdentity::getAppUserId);
    }

    public void deleteIdentitiesForUser(String appUserId) {
        appUserIdentityRepository.deleteByAppUserId(appUserId);
    }

    private void ensureUniqueIdentity(String provider, String externalUserId, String excludedIdentityId) {
        appUserIdentityRepository.findByProviderAndExternalUserId(provider, externalUserId)
                .filter(existing -> !existing.getId().equals(excludedIdentityId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity mapping already exists");
                });
    }

    private AppUser requireManageableUser(String appUserId, HttpServletRequest request) {
        AppUser currentUser = authService.requireAdmin(request);
        AppUser targetUser = authService.findExistingUser(appUserId);
        if (AppRoles.isSuperAdmin(targetUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (AppRoles.isAdmin(targetUser.getRole()) && !AppRoles.isSuperAdmin(currentUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return targetUser;
    }

    private String normalize(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private AppUserIdentityResponse toResponse(AppUserIdentity identity) {
        return AppUserIdentityResponse.builder()
                .id(identity.getId())
                .appUserId(identity.getAppUserId())
                .provider(identity.getProvider())
                .externalUserId(identity.getExternalUserId())
                .createdAt(identity.getCreatedAt())
                .updatedAt(identity.getUpdatedAt())
                .build();
    }
}
