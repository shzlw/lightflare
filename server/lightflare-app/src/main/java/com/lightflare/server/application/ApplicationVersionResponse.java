package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ApplicationVersionResponse(
        String id,
        String applicationId,
        int versionNumber,
        String status,
        OffsetDateTime createdAt
) {
}
