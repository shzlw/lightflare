package com.lightflare.server.scheduler;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ScheduledTaskResponse(
        String id,
        String userId,
        String taskName,
        String taskType,
        String taskDetails,
        boolean enabled,
        String cronExpression,
        OffsetDateTime nextRunAt,
        OffsetDateTime lastStartedAt,
        OffsetDateTime lastCompletedAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastFailureAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
