package com.lightflare.server.scheduler;

import com.lightflare.server.scheduler.ScheduledTask;

public interface ScheduledTaskHandler {

    String taskType();

    void execute(ScheduledTask task);
}
