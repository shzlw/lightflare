package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamFinalResponseEvent(
        String sessionId,
        String content
) implements ChatStreamEventPayload {
}
