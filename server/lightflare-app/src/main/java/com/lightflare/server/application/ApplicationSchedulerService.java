package com.lightflare.server.application;

import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class ApplicationSchedulerService {

    private static final int BATCH_SIZE = 20;
    private static final long LEASE_SECONDS = 300;

    private final ApplicationSchedulerTriggerRepository schedulerTriggerRepository;
    private final ApplicationVersionRepository applicationVersionRepository;
    private final ApplicationEngine applicationEngine;
    private final Executor applicationExecutionExecutor;

    public ApplicationSchedulerService(ApplicationSchedulerTriggerRepository schedulerTriggerRepository,
                                       ApplicationVersionRepository applicationVersionRepository,
                                       ApplicationEngine applicationEngine,
                                       @Qualifier("applicationExecutionExecutor") Executor applicationExecutionExecutor) {
        this.schedulerTriggerRepository = schedulerTriggerRepository;
        this.applicationVersionRepository = applicationVersionRepository;
        this.applicationEngine = applicationEngine;
        this.applicationExecutionExecutor = applicationExecutionExecutor;
    }

    @Scheduled(fixedDelayString = "60000")
    public void runDueCronTriggers() {
        OffsetDateTime now = OffsetDateTime.now();
        String leaseOwner = UUID.randomUUID().toString();
        var triggers = schedulerTriggerRepository.leaseDueCronTriggers(
                now,
                now.plusSeconds(LEASE_SECONDS),
                leaseOwner,
                BATCH_SIZE
        );
        for (ApplicationTrigger trigger : triggers) {
            applicationExecutionExecutor.execute(() -> executeTrigger(trigger, leaseOwner));
        }
    }

    private void executeTrigger(ApplicationTrigger trigger, String leaseOwner) {
        OffsetDateTime completedAt = OffsetDateTime.now();
        Map<String, Object> config = parseConfig(trigger.getConfigJson());
        try {
            ApplicationVersion version = applicationVersionRepository.findById(trigger.getApplicationVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Application version not found: " + trigger.getApplicationVersionId()));
            Map<String, Object> input = readObject(config.get("input"));
            applicationEngine.execute(
                    version.getApplicationId(),
                    input,
                    version.getId(),
                    null,
                    null,
                    trigger.getId(),
                    ApplicationExecutionListener.NOOP
            );

            config.put("lastCompletedAt", completedAt.toString());
            config.put("lastSuccessAt", completedAt.toString());
            config.remove("lastError");
            config.put("nextRunAt", computeNextRunAt(config, completedAt).toString());
            clearLease(config);
            schedulerTriggerRepository.updateLeasedConfig(trigger.getId(), leaseOwner, JsonUtils.toJson(config));
        } catch (Exception exception) {
            log.warn("Scheduled application trigger failed for triggerId={}, applicationVersionId={}",
                    trigger.getId(), trigger.getApplicationVersionId(), exception);
            config.put("lastCompletedAt", completedAt.toString());
            config.put("lastFailureAt", completedAt.toString());
            config.put("lastError", exception.getMessage());
            try {
                config.put("nextRunAt", computeNextRunAt(config, completedAt).toString());
            } catch (Exception ignored) {
                // Leave invalid cron visible for repair.
            }
            clearLease(config);
            schedulerTriggerRepository.updateLeasedConfig(trigger.getId(), leaseOwner, JsonUtils.toJson(config));
        }
    }

    private OffsetDateTime computeNextRunAt(Map<String, Object> config, OffsetDateTime fromTime) {
        String cron = config.get("cron") instanceof String value ? value : null;
        if (!StringUtils.hasText(cron)) {
            throw new IllegalArgumentException("Cron trigger config_json.cron is required.");
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
