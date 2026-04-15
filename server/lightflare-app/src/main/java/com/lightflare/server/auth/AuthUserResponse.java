package com.lightflare.server.auth;

import lombok.Builder;

@Builder
public record AuthUserResponse(
        String id,
        String username,
        String email,
        String displayName,
        String status,
        String role,
        boolean mustChangePassword
) {
}
