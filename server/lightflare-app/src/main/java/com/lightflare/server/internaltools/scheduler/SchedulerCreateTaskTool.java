package com.lightflare.server.internaltools.scheduler;

import com.lightflare.server.scheduler.ScheduledTaskRepository;
import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SchedulerCreateTaskTool implements Tool {

    private static final String DEFAULT_TASK_TYPE = "AGENT_PROMPT";

    private static final String DEFAULT_TASK_NAME = "Scheduled task";

    private static final String USAGE_GUIDANCE = """
            For create-task:
            - `cron_expression` must contain only the Spring cron expression. Spring cron has 6 fields: second minute hour day-of-month month day-of-week.
            - `task_details` must contain only the action to execute later.
            - Never include scheduling instructions inside `task_details`.
            - Remove phrases such as "create a scheduled job", "schedule a task", "run every minute", "run daily", or similar scheduling setup language from `task_details`.
            - Convert natural language schedules yourself:
              - every 1 minute -> `0 * * * * *`
              - every 5 minutes -> `0 */5 * * * *`
              - every hour -> `0 0 * * * *`
              - every day at 9 AM -> `0 0 9 * * *`
            - Example:
              User intent: "create a scheduled job to run every 1 minute, doing ABC"
              cron_expression: `0 * * * * *`
              task_details: "doing ABC"
            - Do not store a prompt that asks the agent to create or modify another scheduled task unless the user explicitly wants recursive scheduling.
            """;

    private static final Pattern[] SCHEDULING_PREFIX_PATTERNS = new Pattern[] {
            Pattern.compile("(?is)^\\s*(?:please\\s+)?(?:create|add|set\\s+up|schedule)\\s+(?:a\\s+)?(?:scheduled\\s+)?(?:job|task|reminder)\\b.*?(?:,|:)\\s*(.+)$"),
            Pattern.compile("(?is)^\\s*(?:please\\s+)?(?:create|add|set\\s+up|schedule)\\s+(?:a\\s+)?(?:scheduled\\s+)?(?:job|task|reminder)\\b.*?\\bto\\b\\s+(?!run\\b)(.+)$"),
            Pattern.compile("(?is)^\\s*(?:please\\s+)?(?:create|add|set\\s+up|schedule)\\b.*?\\brun\\s+(?:every|at|on)\\b.*?(?:,|:)\\s*(.+)$"),
            Pattern.compile("(?is)^\\s*(?:please\\s+)?(?:create|add|set\\s+up|schedule)\\b.*?\\brun\\s+(?:every|at|on)\\b.*?\\bto\\b\\s+(?!run\\b)(.+)$")
    };

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("create-task")
            .description("Create a scheduled task with task details text and a Spring cron expression. For AGENT_PROMPT tasks, task_details must contain only the action to run later, not scheduling instructions. user_id defaults to the current user, task_type defaults to AGENT_PROMPT, and task_name is optional.")
            .category("scheduler")
            .integrationId("scheduler")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("user_id")
                            .type("string")
                            .description("The user ID to create the task for. Defaults to the current user.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("task_name")
                            .type("string")
                            .description("A descriptive name for the task.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("task_type")
                            .type("string")
                            .description("The type of task (e.g., AGENT_PROMPT).")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("cron_expression")
                            .type("string")
                            .description("A 6-field Spring cron expression (second minute hour day-of-month month day-of-week). Example: '0 * * * * *' for every minute.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("task_details")
                            .type("string")
                            .description("The payload or action text for the task. For AGENT_PROMPT, this is the instruction to execute.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("enabled")
                            .type("boolean")
                            .description("Whether the task should start in an enabled state.")
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
        try {
            String userId = firstNonBlank(getOptionalStringArgument(arguments, "user_id"), context != null ? context.userId() : null);
            if (!StringUtils.hasText(userId)) {
                return ToolResult.failure("Missing required argument: user_id. No current user is available in tool execution context.");
            }
            String taskName = firstNonBlank(getOptionalStringArgument(arguments, "task_name"), DEFAULT_TASK_NAME);
            String taskType = firstNonBlank(getOptionalStringArgument(arguments, "task_type"), DEFAULT_TASK_TYPE);
            String cronExpressionValue = normalizeCronExpression(getRequiredStringArgument(arguments, "cron_expression"));
            String taskDetails = normalizeTaskDetails(
                    taskType,
                    getRequiredStringArgument(arguments, "task_details")
            );
            boolean enabled = getOptionalBooleanArgument(arguments, "enabled", true);

            CronExpression cronExpression = parseCronExpression(cronExpressionValue);
            OffsetDateTime now = DateUtils.now();
            OffsetDateTime nextRunAt = cronExpression.next(now);
            if (nextRunAt == null) {
                return ToolResult.failure("Cron expression does not produce a future execution time: " + cronExpressionValue);
            }

            String id = UUID.randomUUID().toString();
            int inserted = scheduledTaskRepository.insertScheduledTask(
                    id,
                    userId,
                    taskName,
                    taskType,
                    taskDetails,
                    enabled,
                    cronExpressionValue,
                    nextRunAt,
                    now,
                    now
            );
            if (inserted != 1) {
                return ToolResult.failure("Expected one scheduled task row to be inserted but got " + inserted);
            }

            return ToolResult.success("Created scheduled task id=%s, taskType=%s, cronExpression=%s, nextRunAt=%s"
                    .formatted(id, taskType, cronExpressionValue, nextRunAt));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.failure(e.getMessage() != null ? e.getMessage() : "Failed to create scheduled task");
        }
    }

    private String getRequiredStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing required argument: " + name));
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean getOptionalBooleanArgument(List<ToolArgument> arguments, String name, boolean defaultValue) {
        return arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asBoolean)
                .findFirst()
                .orElse(defaultValue);
    }

    private CronExpression parseCronExpression(String cronExpressionValue) {
        try {
            return CronExpression.parse(cronExpressionValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cron_expression. Expected Spring cron format with seconds, for example every 1 minute is `0 * * * * *`: " + cronExpressionValue, e);
        }
    }

    private String normalizeCronExpression(String rawCronExpression) {
        String normalized = rawCronExpression.trim();
        if (normalized.matches("^\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+$")) {
            return normalized;
        }

        String lower = normalized.toLowerCase();
        java.util.regex.Matcher everyMinuteMatcher = Pattern
                .compile("\\b(?:run\\s+)?every\\s+(\\d+)\\s+min(?:ute)?s?\\b")
                .matcher(lower);
        if (everyMinuteMatcher.find()) {
            int minutes = Integer.parseInt(everyMinuteMatcher.group(1));
            if (minutes <= 0 || minutes > 59) {
                throw new IllegalArgumentException("Minute interval must be between 1 and 59: " + rawCronExpression);
            }
            return minutes == 1 ? "0 * * * * *" : "0 */%d * * * *".formatted(minutes);
        }

        if (Pattern.compile("\\b(?:run\\s+)?every\\s+min(?:ute)?\\b").matcher(lower).find()) {
            return "0 * * * * *";
        }
        if (Pattern.compile("\\b(?:run\\s+)?every\\s+hour\\b").matcher(lower).find()) {
            return "0 0 * * * *";
        }

        java.util.regex.Matcher dailyAtHourMatcher = Pattern
                .compile("\\b(?:run\\s+)?every\\s+day\\s+at\\s+(\\d{1,2})(?:\\s*(am|pm))?\\b")
                .matcher(lower);
        if (dailyAtHourMatcher.find()) {
            int hour = Integer.parseInt(dailyAtHourMatcher.group(1));
            String meridiem = dailyAtHourMatcher.group(2);
            if (meridiem != null) {
                if (hour < 1 || hour > 12) {
                    throw new IllegalArgumentException("Hour must be between 1 and 12 when using AM/PM: " + rawCronExpression);
                }
                if ("pm".equals(meridiem) && hour < 12) {
                    hour += 12;
                } else if ("am".equals(meridiem) && hour == 12) {
                    hour = 0;
                }
            }
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException("Hour must be between 0 and 23: " + rawCronExpression);
            }
            return "0 0 %d * * *".formatted(hour);
        }

        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private String normalizeTaskDetails(String taskType, String rawTaskDetails) {
        String normalized = rawTaskDetails.trim();
        if (!DEFAULT_TASK_TYPE.equalsIgnoreCase(taskType)) {
            return normalized;
        }

        for (Pattern pattern : SCHEDULING_PREFIX_PATTERNS) {
            java.util.regex.Matcher matcher = pattern.matcher(normalized);
            if (matcher.matches()) {
                String candidate = matcher.group(1);
                if (StringUtils.hasText(candidate)) {
                    return stripLeadingActionMarker(candidate.trim());
                }
            }
        }

        return stripLeadingActionMarker(normalized);
    }

    private String stripLeadingActionMarker(String value) {
        return value.replaceFirst("(?is)^\\s*to\\s+", "").trim();
    }
}
