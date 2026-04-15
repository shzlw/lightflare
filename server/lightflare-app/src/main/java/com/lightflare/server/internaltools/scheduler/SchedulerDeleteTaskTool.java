package com.lightflare.server.internaltools.scheduler;

import com.lightflare.server.scheduler.ScheduledTaskRepository;
import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchedulerDeleteTaskTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("delete-task")
            .description("Delete a scheduled task by id.")
            .category("scheduler")
            .integrationId("scheduler")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("task_id")
                            .type("string")
                            .required(true)
                            .build()
            ))
            .build();

    private final ScheduledTaskRepository scheduledTaskRepository;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String taskId = arguments.stream()
                .filter(argument -> "task_id".equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (taskId == null) {
            return ToolResult.failure("Missing required argument: task_id");
        }

        int deleted = scheduledTaskRepository.deleteScheduledTaskById(taskId);
        if (deleted == 0) {
            return ToolResult.failure("Scheduled task not found: " + taskId);
        }
        if (deleted != 1) {
            return ToolResult.failure("Expected one scheduled task row to be deleted but got " + deleted);
        }
        return ToolResult.success("Deleted scheduled task id=" + taskId);
    }
}
