package com.lightflare.server.scheduler;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("scheduled_task")
@Data
public class ScheduledTask {

    private String id;
    private String userId;
    private String taskName;
    private String taskType;
    private String taskDetails;
    private boolean enabled;
    private String cronExpression;
    private OffsetDateTime nextRunAt;
    private OffsetDateTime lastStartedAt;
    private OffsetDateTime lastCompletedAt;
    private OffsetDateTime lastSuccessAt;
    private OffsetDateTime lastFailureAt;
    private String lastError;
    private String leaseOwner;
    private OffsetDateTime leaseUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
