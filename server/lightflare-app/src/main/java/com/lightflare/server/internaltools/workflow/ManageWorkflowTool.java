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
            <purpose>
            Use this tool when the user wants to create, update, inspect, enable, disable, run, or delete a workflow.
            A workflow is the reusable executable plan. A trigger is how the workflow starts.
            All workflow design changes should be saved through action `upsert`.
            All trigger changes should be saved through `create-trigger`, `update-trigger`, or `delete-trigger`.
            </purpose>

            <mental_model>
            workflow:
              - name, description, status, definition_json
              - definition_json contains inputs and ordered steps

            workflow_trigger:
              - trigger_type: manual, webhook, or scheduler
              - config_json contains type-specific trigger settings

            workflow_run:
              - created when a workflow is run manually, by webhook, by scheduler, or from chat
              - inspect with `runs` and `run-steps`

            Do not create standalone scheduler jobs. If the user asks for a scheduled task, create a workflow and add a scheduler trigger.
            </mental_model>

            <actions>
            schema:
              Return quick machine-readable help.

            list:
              List workflows.

            get:
              Required: workflow_id.
              Return the workflow, triggers, and recent runs.

            upsert:
              Create or update a workflow.
              Create when workflow_id is omitted.
              Update when workflow_id is provided.
              Arguments: workflow_id, name, description, status, definition_json, triggers.
              Important: if the user asks to create a new workflow and add triggers in the same request, pass the triggers array to upsert.
              This avoids needing a second tool call that depends on remembering the newly returned workflow_id.

            delete:
              Required: workflow_id.
              Delete the workflow and its triggers/runs by cascade.

            enable:
              Required: workflow_id, enabled.
              Sets workflow status to active when enabled=true, disabled when enabled=false.

            create-trigger:
              Required: workflow_id, trigger_type.
              Optional: name, enabled, config_json.

            update-trigger:
              Required: workflow_id, trigger_id.
              Optional: trigger_type, name, enabled, config_json.

            delete-trigger:
              Required: workflow_id, trigger_id.

            run:
              Required: workflow_id.
              Optional: trigger_id, input_data.
              If trigger_id is present, run as that trigger type.
              If trigger_id is absent, run as manual.

            runs:
              Required: workflow_id.
              Return recent workflow runs.

            run-steps:
              Required: execution_id.
              Return step logs for a workflow run.
            </actions>

            <workflow_definition_schema>
            Store workflow definitions as valid JSON objects. The preferred shape is:

            {
              "version": 1,
              "inputs": [
                {
                  "name": "customerId",
                  "label": "Customer ID",
                  "type": "string",
                  "required": true,
                  "description": "Customer identifier supplied by the trigger or manual run."
                }
              ],
              "steps": [
                {
                  "id": "lookup_customer",
                  "name": "Lookup customer",
                  "type": "tool",
                  "toolName": "postgres.query",
                  "input": {
                    "customerId": "{{inputs.customerId}}"
                  },
                  "output": {
                    "customer": "{{result}}"
                  },
                  "onError": "stop"
                },
                {
                  "id": "summarize_customer",
                  "name": "Summarize customer",
                  "type": "llm",
                  "prompt": "Summarize this customer and recommend next actions: {{steps.lookup_customer.output.customer}}",
                  "onError": "stop"
                }
              ]
            }

            Keep steps ordered for now. Prefer simple linear workflows. Use clear ids that are stable across edits.
            </workflow_definition_schema>

            <step_types>
            llm:
              Use when the step should reason, summarize, classify, draft, decide, or use tools best-effort.
              Important fields:
                - id
                - name
                - type: "llm"
                - prompt
                - input optional
                - output optional
                - onError optional

              Example:
              {
                "id": "summarize_tasks",
                "name": "Summarize tasks",
                "type": "llm",
                "prompt": "Find my open tasks and summarize what needs attention today.",
                "onError": "stop"
              }

            tool:
              Use when the workflow should call one known tool directly.
              Important fields:
                - id
                - name
                - type: "tool"
                - toolName
                - input
                - output optional
                - onError optional

              Example:
              {
                "id": "send_email",
                "name": "Send summary email",
                "type": "tool",
                "toolName": "email.send",
                "input": {
                  "to": "{{inputs.email}}",
                  "subject": "Daily task summary",
                  "body": "{{steps.summarize_tasks.output.text}}"
                },
                "onError": "stop"
              }

            condition:
              Use only for simple skip/branch metadata until the engine fully supports branching.
              Prefer linear steps unless the user explicitly asks for conditional behavior.

              Example:
              {
                "id": "check_count",
                "name": "Check if there are tasks",
                "type": "condition",
                "if": "{{steps.find_tasks.output.count > 0}}"
              }
            </step_types>

            <expressions>
            Use {{...}} placeholders for values resolved at runtime.
            Common roots:
              - inputs.<name>
              - trigger.<field>
              - steps.<stepId>.output
              - steps.<stepId>.error
              - run.<field>

            Examples:
              "{{inputs.customerId}}"
              "{{trigger.payload.issueId}}"
              "{{steps.lookup_customer.output.email}}"
              "{{steps.summarize.output.text}}"

            Keep expressions simple. Do not invent advanced expression syntax unless the user needs it.
            </expressions>

            <manual_trigger>
            Use manual triggers when a user should click Run and optionally enter fields.

            config_json example:
            {
              "inputFields": [
                {
                  "name": "customerId",
                  "label": "Customer ID",
                  "type": "string",
                  "required": true,
                  "description": "Customer to process."
                },
                {
                  "name": "sendEmail",
                  "label": "Send email",
                  "type": "boolean",
                  "required": false,
                  "default": false
                }
              ]
            }

            Tool call example:
            {
              "action": "create-trigger",
              "workflow_id": "wf_123",
              "trigger_type": "manual",
              "name": "Run with customer",
              "enabled": true,
              "config_json": {
                "inputFields": [
                  {"name": "customerId", "label": "Customer ID", "type": "string", "required": true}
                ]
              }
            }
            </manual_trigger>

            <scheduler_trigger>
            Use scheduler triggers when the user asks for recurring execution, such as every morning, hourly, every Friday, or daily at 9am.
            Store all scheduler data in config_json. Do not create scheduler table rows or standalone scheduler jobs.

            Required config_json fields:
              - cron: cron expression
              - timezone: IANA timezone, for example America/Chicago

            Optional config_json fields:
              - input: object passed as workflow input
              - nextRunAt: server may compute this
              - lastStartedAt, lastCompletedAt, lastSuccessAt, lastFailureAt, lastError: server-maintained runtime metadata

            config_json example:
            {
              "cron": "0 8 * * *",
              "timezone": "America/Chicago",
              "input": {}
            }

            User request example:
              "Every morning, summarize my tasks."

            Correct behavior:
              Use a single upsert call with definition_json and triggers.

            Tool call example:
            {
              "action": "upsert",
              "name": "Daily task summary",
              "status": "draft",
              "definition_json": {
                "version": 1,
                "inputs": [],
                "steps": [
                  {
                    "id": "summarize_tasks",
                    "name": "Summarize open tasks",
                    "type": "llm",
                    "prompt": "Find my open tasks and summarize what needs attention today.",
                    "onError": "stop"
                  }
                ]
              },
              "triggers": [
                {
                  "trigger_type": "scheduler",
                  "name": "Every morning",
                  "enabled": true,
                  "config_json": {
                    "cron": "0 8 * * *",
                    "timezone": "America/Chicago",
                    "input": {}
                  }
                }
              ]
            }
            </scheduler_trigger>

            <webhook_trigger>
            Use webhook triggers when an external system should start the workflow through HTTP.
            Store all webhook settings in config_json.

            config_json example:
            {
              "path": "/api/v1/workflow-webhooks/customer-summary",
              "inputMapping": {
                "customerId": "{{body.customer_id}}",
                "source": "{{headers.x-source}}"
              },
              "auth": {
                "mode": "secret_header",
                "header": "X-Lightflare-Secret"
              }
            }

            Tool call example:
            {
              "action": "create-trigger",
              "workflow_id": "wf_123",
              "trigger_type": "webhook",
              "name": "Customer summary webhook",
              "enabled": true,
              "config_json": {
                "path": "/api/v1/workflow-webhooks/customer-summary",
                "inputMapping": {
                  "customerId": "{{body.customer_id}}"
                }
              }
            }
            </webhook_trigger>

            <create_workflow_example>
            User: "Create a workflow that summarizes my tasks every morning."

            Use one upsert call with a triggers array:
            {
              "action": "upsert",
              "name": "Daily task summary",
              "description": "Summarize open tasks every morning.",
              "status": "active",
              "definition_json": {
                "version": 1,
                "inputs": [],
                "steps": [
                  {
                    "id": "summarize_tasks",
                    "name": "Summarize open tasks",
                    "type": "llm",
                    "prompt": "Find my open tasks and summarize what needs attention today.",
                    "onError": "stop"
                  }
                ]
              },
              "triggers": [
                {
                  "trigger_type": "scheduler",
                  "name": "Every morning",
                  "enabled": true,
                  "config_json": {
                    "cron": "0 8 * * *",
                    "timezone": "America/Chicago",
                    "input": {}
                  }
                }
              ]
            }
            </create_workflow_example>

            <update_workflow_example>
            User: "Add a final email step to this workflow."

            First call get with workflow_id to retrieve current definition and triggers.
            Then call upsert with the full updated definition_json. Preserve existing steps unless the user asks to remove them.

            Example upsert:
            {
              "action": "upsert",
              "workflow_id": "wf_123",
              "name": "Daily task summary",
              "description": "Summarize open tasks every morning and send the summary.",
              "status": "active",
              "definition_json": {
                "version": 1,
                "inputs": [
                  {"name": "email", "label": "Email", "type": "string", "required": true}
                ],
                "steps": [
                  {
                    "id": "summarize_tasks",
                    "name": "Summarize open tasks",
                    "type": "llm",
                    "prompt": "Find my open tasks and summarize what needs attention today.",
                    "onError": "stop"
                  },
                  {
                    "id": "send_summary",
                    "name": "Send summary email",
                    "type": "tool",
                    "toolName": "email.send",
                    "input": {
                      "to": "{{inputs.email}}",
                      "subject": "Daily task summary",
                      "body": "{{steps.summarize_tasks.output.text}}"
                    },
                    "onError": "stop"
                  }
                ]
              }
            }
            </update_workflow_example>

            <run_and_logs_examples>
            Run workflow manually:
            {
              "action": "run",
              "workflow_id": "wf_123",
              "input_data": {
                "customerId": "cust_001"
              }
            }

            Run workflow through a specific trigger:
            {
              "action": "run",
              "workflow_id": "wf_123",
              "trigger_id": "trig_456",
              "input_data": {
                "customerId": "cust_001"
              }
            }

            List recent runs:
            {
              "action": "runs",
              "workflow_id": "wf_123"
            }

            Inspect step logs:
            {
              "action": "run-steps",
              "execution_id": "run_123"
            }
            </run_and_logs_examples>

            <status_and_safety>
            Use status=draft for unfinished workflows.
            Use status=active when the workflow is ready to run.
            Use action=enable with enabled=false to disable a workflow.
            Do not delete workflows unless the user clearly asks to delete them.
            When updating a workflow, preserve existing ids, inputs, triggers, and steps unless the user asks for a replacement.
            If required details are missing, ask a concise follow-up question before creating an active scheduler or webhook trigger.
            </status_and_safety>
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
        return triggerDefinitions.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(trigger -> workflowService.createTrigger(
                        workflowId,
                        triggerType(trigger),
                        stringValue(trigger.get("name")),
                        booleanValue(trigger.get("enabled"), true),
                        jsonValue(trigger.get("config_json"), trigger.get("configJson"), trigger.get("config"))
                ))
                .toList();
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
