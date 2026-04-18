package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamEvent(
        ChatStreamEventType type,
        String executionId,
        ChatStreamEventPayload payload
) {
}
