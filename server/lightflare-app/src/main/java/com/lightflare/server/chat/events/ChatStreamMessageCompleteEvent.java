package com.lightflare.server.chat.events;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record ChatStreamMessageCompleteEvent(
        String messageId,
        String executionId,
        String source,
        String content,
        OffsetDateTime createdAt,
        List<String> artifactIds
) implements ChatStreamEventPayload {
}
