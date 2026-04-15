package com.lightflare.server.contextsearch;

public record ContextSearchRequest(
        String query,
        String sessionId,
        String ownerUserId,
        boolean isAdmin,
        ContextSearchTarget target,
        int limit
) {
}
