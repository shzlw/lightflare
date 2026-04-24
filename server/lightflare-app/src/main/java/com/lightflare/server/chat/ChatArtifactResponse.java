package com.lightflare.server.chat;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ChatArtifactResponse(
        String id,
        String sessionId,
        String messageId,
        String artifactType,
        String title,
        String content,
        String metadata,
        Boolean pinned,
        Integer displayOrder,
        String createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
