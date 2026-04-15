package com.lightflare.server.user;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record UserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String status,
        String role,
        boolean mustChangePassword,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
