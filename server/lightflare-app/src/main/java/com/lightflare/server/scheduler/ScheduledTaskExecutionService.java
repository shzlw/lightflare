package com.lightflare.server.scheduler;

import com.lightflare.server.scheduler.ScheduledTask;
import com.lightflare.server.scheduler.ScheduledTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskExecutionService {

    private final ScheduledTaskHandlerRegistry handlerRegistry;
    private final ScheduledTaskRepository scheduledTaskRepository;

    @Async("scheduledTaskExecutor")
    public void executeAsync(ScheduledTask task, String leaseOwner) {
        OffsetDateTime completedAt = OffsetDateTime.now();

        try {
            ScheduledTaskHandler handler = handlerRegistry.find(task.getTaskType())
                    .orElseThrow(() -> new IllegalStateException("No scheduled task handler registered for type: " + task.getTaskType()));
            handler.execute(task);
            OffsetDateTime nextRunAt = computeNextRunAt(task, completedAt);
            boolean updated = scheduledTaskRepository.markSuccess(task.getId(), leaseOwner, completedAt, nextRunAt);
            if (!updated) {
                log.warn("Scheduled task success update skipped because lease ownership changed for taskId={}", task.getId());
            }
            log.info("Completed scheduled task id={}, taskType={}, nextRunAt={}", task.getId(), task.getTaskType(), nextRunAt);
        } catch (RuntimeException e) {
            OffsetDateTime nextRunAt = computeFailureNextRunAt(task, completedAt);
            boolean updated = scheduledTaskRepository.markFailure(
                    task.getId(),
                    leaseOwner,
                    completedAt,
                    nextRunAt,
                    truncateErrorMessage(e.getMessage())
            );
            if (!updated) {
                log.warn("Scheduled task failure update skipped because lease ownership changed for taskId={}", task.getId());
            }
            log.warn("Scheduled task execution failed for id={}, taskType={}", task.getId(), task.getTaskType(), e);
        }
    }

    private OffsetDateTime computeNextRunAt(ScheduledTask task, OffsetDateTime fromTime) {
        if (!StringUtils.hasText(task.getCronExpression())) {
            throw new IllegalStateException("Scheduled task is missing cron expression");
        }

        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(task.getCronExpression());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Scheduled task has invalid cron expression: " + task.getCronExpression(), e);
        }

        OffsetDateTime nextRunAt = cronExpression.next(fromTime);
        if (nextRunAt == null) {
            throw new IllegalStateException("Scheduled task cron expression does not produce a future execution time: " + task.getCronExpression());
        }
        return nextRunAt;
    }

    private OffsetDateTime computeFailureNextRunAt(ScheduledTask task, OffsetDateTime fromTime) {
        try {
            return computeNextRunAt(task, fromTime);
        } catch (RuntimeException ex) {
            log.warn("Falling back to immediate retry schedule for taskId={} because next cron run could not be computed", task.getId(), ex);
            return fromTime.plusMinutes(1);
        }
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "Scheduled task execution failed";
        }
        return errorMessage.length() <= 4000 ? errorMessage : errorMessage.substring(0, 4000);
    }
}
