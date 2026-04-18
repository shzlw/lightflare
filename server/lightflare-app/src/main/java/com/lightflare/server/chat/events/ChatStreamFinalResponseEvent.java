package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamFinalResponseEvent(
        String executionId,
        String content
) implements ChatStreamEventPayload {
}
