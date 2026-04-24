package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ApplicationResponse(
        String id,
        String name,
        String description,
        String createdBy,
        String sourceChatSessionId,
        String publishedVersionId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
