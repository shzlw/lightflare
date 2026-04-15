package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamMessageErrorEvent(
        String sessionId,
        String message
) implements ChatStreamEventPayload {
}
