package com.lightflare.server.chat.events;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ChatStreamMessageCompleteEvent(
        String messageId,
        String executionId,
        String source,
        String content,
        OffsetDateTime createdAt
) implements ChatStreamEventPayload {
}
