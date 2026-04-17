package com.lightflare.server.workflow;

import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class WorkflowSchedulerService {

    private static final int BATCH_SIZE = 20;
    private static final long LEASE_SECONDS = 300;

    private final WorkflowSchedulerTriggerRepository schedulerTriggerRepository;
    private final WorkflowEngine workflowEngine;
    private final Executor workflowSchedulerExecutor;

    public WorkflowSchedulerService(WorkflowSchedulerTriggerRepository schedulerTriggerRepository,
                                    WorkflowEngine workflowEngine,
                                    @Qualifier("workflowSchedulerExecutor") Executor workflowSchedulerExecutor) {
        this.schedulerTriggerRepository = schedulerTriggerRepository;
        this.workflowEngine = workflowEngine;
        this.workflowSchedulerExecutor = workflowSchedulerExecutor;
    }

    @Scheduled(fixedDelayString = "60000")
    public void runDueSchedulerTriggers() {
        OffsetDateTime now = OffsetDateTime.now();
        String leaseOwner = UUID.randomUUID().toString();
        var triggers = schedulerTriggerRepository.leaseDueSchedulerTriggers(
                now,
                now.plusSeconds(LEASE_SECONDS),
                leaseOwner,
                BATCH_SIZE
        );
        for (WorkflowTrigger trigger : triggers) {
            workflowSchedulerExecutor.execute(() -> executeTrigger(trigger, leaseOwner));
        }
    }

    private void executeTrigger(WorkflowTrigger trigger, String leaseOwner) {
        OffsetDateTime completedAt = OffsetDateTime.now();
        Map<String, Object> config = parseConfig(trigger.getConfigJson());
        try {
            Map<String, Object> input = readObject(config.get("input"));
            workflowEngine.execute(
                    trigger.getWorkflowId(),
                    input,
                    null,
                    null,
                    "scheduler",
                    trigger.getId(),
                    trigger.getId()
            );

            config.put("lastCompletedAt", completedAt.toString());
            config.put("lastSuccessAt", completedAt.toString());
            config.remove("lastError");
            config.put("nextRunAt", computeNextRunAt(config, completedAt).toString());
            clearLease(config);
            schedulerTriggerRepository.updateLeasedConfig(
                    trigger.getId(),
                    leaseOwner,
                    JsonUtils.toJson(config),
                    completedAt
            );
        } catch (Exception e) {
            log.warn("Scheduled workflow trigger failed for triggerId={}, workflowId={}",
                    trigger.getId(), trigger.getWorkflowId(), e);
            config.put("lastCompletedAt", completedAt.toString());
            config.put("lastFailureAt", completedAt.toString());
            config.put("lastError", e.getMessage());
            try {
                config.put("nextRunAt", computeNextRunAt(config, completedAt).toString());
            } catch (Exception ignored) {
                // Leave the broken schedule visible in config_json for the user to fix.
            }
            clearLease(config);
            schedulerTriggerRepository.updateLeasedConfig(
                    trigger.getId(),
                    leaseOwner,
                    JsonUtils.toJson(config),
                    completedAt
            );
        }
    }

    private OffsetDateTime computeNextRunAt(Map<String, Object> config, OffsetDateTime fromTime) {
        String cron = config.get("cron") instanceof String value ? value : null;
        if (!StringUtils.hasText(cron)) {
            throw new IllegalArgumentException("Scheduler trigger config_json.cron is required.");
        }
        String timezone = config.get("timezone") instanceof String value && StringUtils.hasText(value)
                ? value
                : "UTC";
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime next = CronExpression.parse(cron).next(fromTime.atZoneSameInstant(zoneId));
        if (next == null) {
            throw new IllegalStateException("Cron expression does not produce a future run: " + cron);
        }
        return next.toOffsetDateTime();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        Object parsed = JsonUtils.fromJson(StringUtils.hasText(configJson) ? configJson : "{}");
        if (!(parsed instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        return new HashMap<>((Map<String, Object>) map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return Collections.emptyMap();
    }

    private void clearLease(Map<String, Object> config) {
        config.remove("leaseOwner");
        config.remove("leaseUntil");
    }
}
