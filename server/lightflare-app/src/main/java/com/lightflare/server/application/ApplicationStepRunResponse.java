package com.lightflare.server.application;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ApplicationStepRunResponse(
        String id,
        String applicationRunId,
        String stepId,
        String status,
        String inputJson,
        String outputJson,
        String errorMessage,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
