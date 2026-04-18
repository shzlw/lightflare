package com.lightflare.server.chat.events;

import lombok.Builder;

@Builder
public record ChatStreamMessageStartEvent(
        String messageId,
        String executionId,
        String source
) implements ChatStreamEventPayload {
}
