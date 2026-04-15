package com.lightflare.server.chat;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ChatSessionResponse(
    String id,
    String title,
    String userId,
    Integer totalTokens,
    Integer totalInputTokens,
    Integer totalOutputTokens,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
