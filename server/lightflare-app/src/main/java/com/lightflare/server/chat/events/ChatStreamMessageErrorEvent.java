package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamMessageErrorEvent(
        String executionId,
        String message
) implements ChatStreamEventPayload {
}
