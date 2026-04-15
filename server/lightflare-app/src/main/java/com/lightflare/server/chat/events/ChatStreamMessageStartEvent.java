package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamMessageStartEvent(
        String messageId,
        String sessionId,
        String source
) implements ChatStreamEventPayload {
}
