package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import com.lightflare.server.workflow.Workflow;
import com.lightflare.server.workflow.WorkflowEngine;
import com.lightflare.server.workflow.WorkflowService;
import com.lightflare.server.workflow.WorkflowTrigger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ManageWorkflowTool implements InternalTool {

    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    private static final String USAGE_GUIDANCE = """
            Use this tool to manage Lightflare workflows from chat.

            Workflows store executable definitions as JSON text. Triggers are stored separately in workflow_trigger.
            Valid trigger types are manual, webhook, and scheduler.

            Scheduler trigger config_json example:
            {
              "cron": "0 8 * * *",
              "timezone": "America/Chicago",
              "input": {}
            }

            Manual trigger config_json example:
            {
              "inputFields": [
                {"name": "customerId", "label": "Customer ID", "type": "string", "required": true}
              ]
            }

            Webhook trigger config_json example:
            {
              "path": "/api/v1/workflow-webhooks/example",
              "inputMapping": {}
            }
            """;

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

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
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
        if (StringUtils.hasText(workflowId)) {
            Workflow updated = workflowService.updateWorkflow(
                    workflowId,
                    optionalString(arguments, "name"),
                    optionalString(arguments, "description"),
                    definitionJson,
                    optionalString(arguments, "status")
            );
            return success(updated);
        }
        Workflow created = workflowService.createWorkflow(
                optionalString(arguments, "name"),
                optionalString(arguments, "description"),
                definitionJson,
                optionalString(arguments, "status"),
                context != null ? context.userId() : null
        );
        return success(created);
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

    private ToolArgument findArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .findFirst()
                .orElse(null);
    }
}
