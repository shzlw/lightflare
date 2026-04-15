package com.lightflare.server.messaging;

public record MessagingAppConnectorRequest(
        String sessionId,
        String userId,
        String message
) {

    public MessagingAppConnectorRequest {
        sessionId = requireText(sessionId, "sessionId");
        userId = requireText(userId, "userId");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
