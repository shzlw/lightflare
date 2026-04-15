package com.lightflare.server.execution;

import java.time.OffsetDateTime;

public record ExecutionCheckpoint(
        String id,
        String executionId,
        String executionType,
        String status,
        String referenceType,
        String referenceId,
        String payload,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
