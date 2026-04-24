package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ApplicationRunResponse(
        String id,
        String applicationId,
        String applicationVersionId,
        String triggerId,
        String status,
        String inputJson,
        String outputJson,
        String errorMessage,
        String startedBy,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
