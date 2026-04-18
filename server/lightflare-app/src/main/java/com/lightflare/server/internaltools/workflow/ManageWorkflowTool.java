package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.FileUtils;
import com.lightflare.server.utils.JsonUtils;
import com.lightflare.server.workflow.Workflow;
import com.lightflare.server.workflow.WorkflowEngine;
import com.lightflare.server.workflow.WorkflowService;
import com.lightflare.server.workflow.WorkflowTrigger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ManageWorkflowTool {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("manage-workflow.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-workflow")
            .description("Create, update, delete, enable, run, and inspect workflows and workflow triggers.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("action")
                            .type("string")
                            .description("schema, list, get, upsert, delete, enable, create-trigger, update-trigger, delete-trigger, run, runs, or run-steps.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("workflow_id")
                            .type("string")
                            .description("Workflow id for get/update/delete/enable/trigger/run actions.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("trigger_id")
                            .type("string")
                            .description("Workflow trigger id for update-trigger/delete-trigger/run.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("name")
                            .type("string")
                            .description("Workflow or trigger display name.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("description")
                            .type("string")
                            .description("Workflow description.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("status")
                            .type("string")
                            .description("Workflow status, such as draft, active, or disabled.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("enabled")
                            .type("boolean")
                            .description("Enable/disable workflow or trigger.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("definition_json")
                            .type("string")
                            .description("Workflow definition JSON as string or object.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("triggers")
                            .type("array")
                            .description("Optional trigger definitions to create during upsert. Each item supports trigger_type/type, name, enabled, and config_json/config.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("trigger_type")
                            .type("string")
                            .description("manual, webhook, or scheduler.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("config_json")
                            .type("string")
                            .description("Trigger config JSON as string or object.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("input_data")
                            .type("object")
                            .description("Input object for workflow execution.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("execution_id")
                            .type("string")
                            .description("Workflow run id for run-steps.")
                            .required(false)
                            .build()
            ))
            .build();

    public ToolDefinition definition() {
        return DEFINITION;
    }

    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String action = requiredString(arguments, "action");
        try {
            return switch (action.trim().toLowerCase()) {
                case "schema", "help" -> success(Map.of(
                        "workflowDefinition", Map.of("version", 1, "inputs", List.of(), "steps", List.of()),
                        "triggerTypes", List.of("manual", "webhook", "scheduler"),
                        "actions", List.of("list", "get", "upsert", "delete", "enable", "create-trigger",
                                "update-trigger", "delete-trigger", "run", "runs", "run-steps")
                ));
                case "list" -> success(workflowService.getAllWorkflows());
                case "get" -> handleGet(arguments);
                case "upsert" -> handleUpsert(arguments, context);
                case "delete" -> handleDelete(arguments);
                case "enable" -> handleEnable(arguments);
                case "create-trigger" -> handleCreateTrigger(arguments);
                case "update-trigger" -> handleUpdateTrigger(arguments);
                case "delete-trigger" -> handleDeleteTrigger(arguments);
                case "run" -> handleRun(arguments, context);
                case "runs" -> handleRuns(arguments);
                case "run-steps" -> handleRunSteps(arguments);
                default -> ToolResult.failure("Unknown workflow action: " + action);
            };
        } catch (Exception e) {
            return ToolResult.failure("Workflow action failed: " + e.getMessage());
        }
    }

    public ToolResult listWorkflows() {
        return success(workflowService.getAllWorkflows());
    }

    public ToolResult getWorkflow(List<ToolArgument> arguments) {
        return handleGet(arguments);
    }

    public ToolResult createWorkflow(List<ToolArgument> arguments, ToolExecutionContext context) {
        if (StringUtils.hasText(optionalString(arguments, "workflow_id"))) {
            throw new IllegalArgumentException("create-workflow does not accept workflow_id. Use update-workflow to modify an existing workflow.");
        }
        return handleUpsert(arguments, context);
    }

    public ToolResult updateWorkflow(List<ToolArgument> arguments, ToolExecutionContext context) {
        requiredString(arguments, "workflow_id");
        return handleUpsert(arguments, context);
    }

    public ToolResult deleteWorkflow(List<ToolArgument> arguments) {
        return handleDelete(arguments);
    }

    public ToolResult enableWorkflow(List<ToolArgument> arguments) {
        return handleEnable(arguments);
    }

    public ToolResult manageTrigger(List<ToolArgument> arguments) {
        String action = requiredString(arguments, "action");
        return switch (action.trim().toLowerCase()) {
            case "create" -> handleCreateTrigger(arguments);
            case "update" -> handleUpdateTrigger(arguments);
            case "delete" -> handleDeleteTrigger(arguments);
            default -> ToolResult.failure("Unknown workflow trigger action: " + action);
        };
    }

    public ToolResult runWorkflow(List<ToolArgument> arguments, ToolExecutionContext context) {
        return handleRun(arguments, context);
    }

    public ToolResult workflowRuns(List<ToolArgument> arguments) {
        return handleRuns(arguments);
    }

    public ToolResult workflowRunSteps(List<ToolArgument> arguments) {
        return handleRunSteps(arguments);
    }

    private ToolResult handleGet(List<ToolArgument> arguments) {
        String workflowId = requiredString(arguments, "workflow_id");
        return success(Map.of(
                "workflow", workflowService.getWorkflow(workflowId),
                "triggers", workflowService.getTriggers(workflowId),
                "recentRuns", workflowService.getRecentRuns(workflowId, 10)
        ));
    }

    private ToolResult handleUpsert(List<ToolArgument> arguments, ToolExecutionContext context) {
        String workflowId = optionalString(arguments, "workflow_id");
        String definitionJson = optionalJson(arguments, "definition_json", "schema_definition", "schemaDefinition");
        List<Object> triggerDefinitions = optionalArray(arguments, "triggers", "workflow_triggers", "workflowTriggers");
        if (StringUtils.hasText(workflowId)) {
            Workflow updated = workflowService.updateWorkflow(
                    workflowId,
                    optionalString(arguments, "name"),
                    optionalString(arguments, "description"),
                    definitionJson,
                    optionalString(arguments, "status")
            );
            List<WorkflowTrigger> createdTriggers = createTriggers(updated.getId(), triggerDefinitions);
            return success(Map.of(
                    "workflow", updated,
                    "createdTriggers", createdTriggers,
                    "workflowId", updated.getId()
            ));
        }
        Workflow created = workflowService.createWorkflow(
                optionalString(arguments, "name"),
                optionalString(arguments, "description"),
                definitionJson,
                optionalString(arguments, "status"),
                context != null ? context.userId() : null
        );
        List<WorkflowTrigger> createdTriggers = createTriggers(created.getId(), triggerDefinitions);
        if (createdTriggers.isEmpty()) {
            WorkflowTrigger defaultTrigger = workflowService.createDefaultManualTriggerIfMissing(created.getId());
            createdTriggers = defaultTrigger != null ? List.of(defaultTrigger) : List.of();
        }
        return success(Map.of(
                "workflow", created,
                "createdTriggers", createdTriggers,
                "workflowId", created.getId()
        ));
    }

    private ToolResult handleDelete(List<ToolArgument> arguments) {
        workflowService.deleteWorkflow(requiredString(arguments, "workflow_id"));
        return ToolResult.success("Workflow deleted.");
    }

    private ToolResult handleEnable(List<ToolArgument> arguments) {
        Workflow workflow = workflowService.setWorkflowEnabled(
                requiredString(arguments, "workflow_id"),
                optionalBoolean(arguments, "enabled", true)
        );
        return success(workflow);
    }

    private ToolResult handleCreateTrigger(List<ToolArgument> arguments) {
        WorkflowTrigger trigger = workflowService.createTrigger(
                requiredString(arguments, "workflow_id"),
                requiredString(arguments, "trigger_type"),
                optionalString(arguments, "name"),
                optionalBoolean(arguments, "enabled", true),
                optionalJson(arguments, "config_json", "config")
        );
        return success(trigger);
    }

    private ToolResult handleUpdateTrigger(List<ToolArgument> arguments) {
        WorkflowTrigger trigger = workflowService.updateTrigger(
                requiredString(arguments, "workflow_id"),
                requiredString(arguments, "trigger_id"),
                optionalString(arguments, "trigger_type"),
                optionalString(arguments, "name"),
                optionalBoolean(arguments, "enabled", null),
                optionalJson(arguments, "config_json", "config")
        );
        return success(trigger);
    }

    private ToolResult handleDeleteTrigger(List<ToolArgument> arguments) {
        workflowService.deleteTrigger(requiredString(arguments, "workflow_id"), requiredString(arguments, "trigger_id"));
        return ToolResult.success("Workflow trigger deleted.");
    }

    private ToolResult handleRun(List<ToolArgument> arguments, ToolExecutionContext context) {
        String workflowId = requiredString(arguments, "workflow_id");
        String triggerId = optionalString(arguments, "trigger_id");
        Map<String, Object> inputData = optionalObject(arguments, "input_data", "initial_data");
        String executionId;
        if (StringUtils.hasText(triggerId)) {
            WorkflowTrigger trigger = workflowService.getTriggerForWorkflow(workflowId, triggerId);
            executionId = workflowEngine.execute(
                    workflowId,
                    inputData,
                    null,
                    context != null ? context.userId() : null,
                    trigger.getTriggerType(),
                    trigger.getId(),
                    trigger.getId()
            );
        } else {
            executionId = workflowEngine.execute(
                    workflowId,
                    inputData,
                    null,
                    context != null ? context.userId() : null,
                    "manual",
                    null
            );
        }
        return success(Map.of("executionId", executionId));
    }

    private ToolResult handleRuns(List<ToolArgument> arguments) {
        return success(workflowService.getRecentRuns(requiredString(arguments, "workflow_id"), 20));
    }

    private ToolResult handleRunSteps(List<ToolArgument> arguments) {
        return success(workflowService.getStepRuns(requiredString(arguments, "execution_id")));
    }

    private ToolResult success(Object value) {
        return ToolResult.success(JsonUtils.toJson(value));
    }

    private String requiredString(List<ToolArgument> arguments, String name) {
        String value = optionalString(arguments, name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private String optionalString(List<ToolArgument> arguments, String name) {
        ToolArgument argument = findArgument(arguments, name);
        return argument != null ? argument.asString() : null;
    }

    private Boolean optionalBoolean(List<ToolArgument> arguments, String name, Boolean defaultValue) {
        ToolArgument argument = findArgument(arguments, name);
        return argument != null && argument.getValue() != null ? argument.asBoolean() : defaultValue;
    }

    private String optionalJson(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof String value) {
                return StringUtils.hasText(value) ? value : null;
            }
            return JsonUtils.toJson(argument.getValue());
        }
        return null;
    }

    private Map<String, Object> optionalObject(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof Map<?, ?>) {
                return argument.asObject();
            }
            if (argument.getValue() instanceof String value && StringUtils.hasText(value)) {
                Object parsed = JsonUtils.fromJson(value);
                if (parsed instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> object = (Map<String, Object>) map;
                    return object;
                }
            }
        }
        return Collections.emptyMap();
    }

    private List<Object> optionalArray(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = findArgument(arguments, name);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof List<?>) {
                return argument.asArray();
            }
            if (argument.getValue() instanceof String value && StringUtils.hasText(value)) {
                Object parsed = JsonUtils.fromJson(value);
                if (parsed instanceof List<?> list) {
                    return List.copyOf(list);
                }
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowTrigger> createTriggers(String workflowId, List<Object> triggerDefinitions) {
        if (triggerDefinitions == null || triggerDefinitions.isEmpty()) {
            return List.of();
        }
        Set<String> seenTriggers = new HashSet<>();
        return triggerDefinitions.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(trigger -> seenTriggers.add(triggerKey(trigger)))
                .map(trigger -> workflowService.createTrigger(
                        workflowId,
                        triggerType(trigger),
                        stringValue(trigger.get("name")),
                        booleanValue(trigger.get("enabled"), true),
                        jsonValue(trigger.get("config_json"), trigger.get("configJson"), trigger.get("config"))
                ))
                .toList();
    }

    private String triggerKey(Map<String, Object> trigger) {
        String type = triggerType(trigger).trim().toLowerCase();
        String configJson = jsonValue(trigger.get("config_json"), trigger.get("configJson"), trigger.get("config"));
        if ("scheduler".equals(type)) {
            Map<String, Object> config = parseObject(configJson);
            return String.join("|",
                    type,
                    schedulerCronKey(config.get("cron")),
                    stringValue(config.get("timezone")),
                    JsonUtils.toJson(config.get("input"))
            );
        }
        return type + "|" + stringValue(trigger.get("name")) + "|" + configJson;
    }

    private String schedulerCronKey(Object cronValue) {
        String cron = stringValue(cronValue);
        if (!StringUtils.hasText(cron)) {
            return "";
        }
        String normalized = cron.trim().replaceAll("\\s+", " ");
        String[] fields = normalized.split(" ");
        if (fields.length == 5) {
            return "0 " + normalized;
        }
        if (fields.length == 6 && "*".equals(fields[0])) {
            return "0 " + String.join(" ", java.util.Arrays.copyOfRange(fields, 1, fields.length));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        Object parsed = JsonUtils.fromJson(json);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private String triggerType(Map<String, Object> trigger) {
        String value = stringValue(trigger.get("trigger_type"));
        if (!StringUtils.hasText(value)) {
            value = stringValue(trigger.get("triggerType"));
        }
        if (!StringUtils.hasText(value)) {
            value = stringValue(trigger.get("type"));
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Trigger definition is missing trigger_type.");
        }
        return value;
    }

    private String jsonValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String stringValue) {
                return StringUtils.hasText(stringValue) ? stringValue : null;
            }
            return JsonUtils.toJson(value);
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private ToolArgument findArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .findFirst()
                .orElse(null);
    }
}
