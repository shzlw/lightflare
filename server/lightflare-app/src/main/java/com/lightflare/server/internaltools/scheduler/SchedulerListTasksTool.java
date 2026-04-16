package com.lightflare.server.internaltools.scheduler;

import com.lightflare.server.scheduler.ScheduledTaskRepository;
import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchedulerListTasksTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("list-tasks")
            .description("List scheduled tasks ordered by next run time.")
            .category("scheduler")
            .integrationId("scheduler")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("limit")
                            .type("integer")
                            .description("Maximum number of tasks to return (default 20, max 100)")
                            .required(false)
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
        Integer requestedLimit = arguments.stream()
                .filter(argument -> "limit".equals(argument.getName()))
                .map(ToolArgument::asInteger)
                .findFirst()
                .orElse(20);
        int limit = Math.max(1, Math.min(requestedLimit, 100));

        List<Map<String, Object>> items = scheduledTaskRepository.findScheduledTasks(limit).stream()
                .map(task -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", task.getId());
                    item.put("user_id", task.getUserId());
                    item.put("task_name", task.getTaskName());
                    item.put("task_type", task.getTaskType());
                    item.put("enabled", task.isEnabled());
                    item.put("task_details", task.getTaskDetails());
                    item.put("cron_expression", task.getCronExpression());
                    item.put("next_run_at", task.getNextRunAt());
                    item.put("last_started_at", task.getLastStartedAt());
                    item.put("last_completed_at", task.getLastCompletedAt());
                    item.put("last_success_at", task.getLastSuccessAt());
                    item.put("last_failure_at", task.getLastFailureAt());
                    item.put("last_error", task.getLastError());
                    return item;
                })
                .toList();

        return ToolResult.success(JsonUtils.toJson(items));
    }
}
