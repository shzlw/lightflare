package com.lightflare.server.project;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ProjectResponse(
        String id,
        String title,
        String description,
        String userId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
