package com.lightflare.server.user;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record AppUserIdentityResponse(
        String id,
        String appUserId,
        String provider,
        String externalUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
