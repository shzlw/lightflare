package com.lightflare.server.chat;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ChatMessageResponse(
    String id,
    String sessionId,
    String source,
    String content,
    OffsetDateTime createdAt
) {
}
