package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record MemoryResponse(
        String id,
        String ownerUserId,
        String sessionId,
        String scope,
        String kind,
        String source,
        String retentionPolicy,
        String status,
        String statusReason,
        OffsetDateTime statusChangedAt,
        String statusChangedBy,
        DocumentResponse document,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
