package com.lightflare.server.scheduler;

import com.lightflare.server.config.SchedulerProperties;
import com.lightflare.server.scheduler.ScheduledTask;
import com.lightflare.server.scheduler.ScheduledTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ScheduledTaskPollingService {

    private final ScheduledTaskRepository scheduledTaskRepository;
    private final ScheduledTaskExecutionService scheduledTaskExecutionService;
    private final SchedulerProperties schedulerProperties;
    private final String instanceId;

    public ScheduledTaskPollingService(ScheduledTaskRepository scheduledTaskRepository,
                                       ScheduledTaskExecutionService scheduledTaskExecutionService,
                                       SchedulerProperties schedulerProperties) {
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.scheduledTaskExecutionService = scheduledTaskExecutionService;
        this.schedulerProperties = schedulerProperties;
        this.instanceId = resolveInstanceId();
    }

    @Scheduled(fixedDelayString = "${lightflare.scheduler.poll-interval-ms:60000}")
    public void pollAndDispatch() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseUntil = now.plusSeconds(schedulerProperties.getLeaseDurationSeconds());
        List<ScheduledTask> leasedTasks = scheduledTaskRepository.leaseDueTasks(
                now,
                leaseUntil,
                instanceId,
                schedulerProperties.getBatchSize()
        );
        if (leasedTasks.isEmpty()) {
            return;
        }

        log.info("Leased {} scheduled tasks on instanceId={}", leasedTasks.size(), instanceId);
        for (ScheduledTask task : leasedTasks) {
            scheduledTaskExecutionService.executeAsync(task, instanceId);
        }
    }

    private String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (UnknownHostException e) {
            return "instance-" + UUID.randomUUID();
        }
    }
}
