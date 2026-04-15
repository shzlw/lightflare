package com.lightflare.server.scheduler;

import com.lightflare.server.scheduler.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingScheduledTaskHandler implements ScheduledTaskHandler {

    @Override
    public String taskType() {
        return "LOG";
    }

    @Override
    public void execute(ScheduledTask task) {
        log.info("Executing LOG scheduled task id={}, userId={}, taskName={}, taskDetails={}",
                task.getId(), task.getUserId(), task.getTaskName(), task.getTaskDetails());
    }
}
