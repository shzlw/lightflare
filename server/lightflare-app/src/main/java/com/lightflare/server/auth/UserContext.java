package com.lightflare.server.auth;

public record UserContext(
        String userId,
        String username,
        String role,
        String status
) {
}
