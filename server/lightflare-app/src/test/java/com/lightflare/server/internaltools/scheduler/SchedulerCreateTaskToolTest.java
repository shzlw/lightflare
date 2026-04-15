package com.lightflare.server.internaltools.scheduler;

import com.lightflare.server.scheduler.ScheduledTaskRepository;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerCreateTaskToolTest {

    @Test
    void shouldConvertNaturalMinuteScheduleAndStripSchedulingTextFromTaskDetails() {
        CapturingScheduledTaskRepository scheduledTaskRepository = new CapturingScheduledTaskRepository();
        SchedulerCreateTaskTool tool = new SchedulerCreateTaskTool(scheduledTaskRepository);

        ToolResult result = tool.execute(
                List.of(
                        argument("cron_expression", "every 1 minute"),
                        argument("task_details", "create a new schedule to run every 1 minute, to check the weather for zip code=75036")
                ),
                new ToolExecutionContext(tool.definition(), "user-1")
        );

        assertTrue(result.success());
        assertEquals("user-1", scheduledTaskRepository.userId);
        assertEquals("Scheduled task", scheduledTaskRepository.taskName);
        assertEquals("AGENT_PROMPT", scheduledTaskRepository.taskType);
        assertEquals("check the weather for zip code=75036", scheduledTaskRepository.taskDetails);
        assertEquals(true, scheduledTaskRepository.enabled);
        assertEquals("0 * * * * *", scheduledTaskRepository.cronExpression);
    }

    @Test
    void shouldReturnHelpfulFailureForInvalidCronExpression() {
        SchedulerCreateTaskTool tool = new SchedulerCreateTaskTool(new CapturingScheduledTaskRepository());

        ToolResult result = tool.execute(
                List.of(
                        argument("cron_expression", "not a schedule"),
                        argument("task_details", "check the weather for zip code=75036")
                ),
                new ToolExecutionContext(tool.definition(), "user-1")
        );

        assertEquals(false, result.success());
        assertTrue(result.content().contains("Expected Spring cron format with seconds"));
    }

    private static ToolArgument argument(String name, Object value) {
        return ToolArgument.builder()
                .name(name)
                .value(value)
                .build();
    }

    private static final class CapturingScheduledTaskRepository extends ScheduledTaskRepository {
        private String userId;
        private String taskName;
        private String taskType;
        private String taskDetails;
        private boolean enabled;
        private String cronExpression;

        private CapturingScheduledTaskRepository() {
            super(null);
        }

        @Override
        public int insertScheduledTask(String id,
                                       String userId,
                                       String taskName,
                                       String taskType,
                                       String taskDetails,
                                       boolean enabled,
                                       String cronExpression,
                                       OffsetDateTime nextRunAt,
                                       OffsetDateTime createdAt,
                                       OffsetDateTime updatedAt) {
            this.userId = userId;
            this.taskName = taskName;
            this.taskType = taskType;
            this.taskDetails = taskDetails;
            this.enabled = enabled;
            this.cronExpression = cronExpression;
            return 1;
        }
    }
}
