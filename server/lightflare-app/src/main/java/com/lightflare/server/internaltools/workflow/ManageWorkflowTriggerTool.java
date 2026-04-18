package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManageWorkflowTriggerTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
            <purpose>
            Use this tool to create, update, or delete triggers for an existing workflow.
            A trigger starts a workflow. Supported trigger_type values are manual, webhook, and scheduler.
            </purpose>

            <when_to_use>
            Use this tool when the workflow already exists and the user asks to add/change/remove a trigger.
            For new workflows with triggers in the same request, use create-workflow and include the `triggers` array instead.
            </when_to_use>

            <actions>
            create:
              Required: workflow_id, trigger_type.
              Optional: name, enabled, config_json.

            update:
              Required: workflow_id, trigger_id.
              Optional: trigger_type, name, enabled, config_json.

            delete:
              Required: workflow_id, trigger_id.
            </actions>

            <manual_trigger>
            Use manual when a user clicks Run and may provide input fields.
            config_json:
            {
              "inputFields": [
                {"name": "zip", "label": "ZIP code", "type": "string", "required": true, "default": "75036"}
              ]
            }

            Example:
            {
              "action": "create",
              "workflow_id": "wf_123",
              "trigger_type": "manual",
              "name": "Run manually",
              "enabled": true,
              "config_json": {
                "inputFields": [
                  {"name": "zip", "label": "ZIP code", "type": "string", "required": true}
                ]
              }
            }
            </manual_trigger>

            <scheduler_trigger>
            Use scheduler when the user asks for recurring execution such as every minute, hourly, daily, weekdays, every morning, or every Friday.
            Store scheduler settings only in config_json. Do not create cron columns or standalone scheduler rows.
            Required config_json fields: cron, timezone.
            Optional config_json fields: input.

            Examples:
            Every minute:
            {
              "action": "create",
              "workflow_id": "wf_123",
              "trigger_type": "scheduler",
              "name": "Every minute",
              "enabled": true,
              "config_json": {"cron": "* * * * *", "timezone": "America/Chicago", "input": {}}
            }

            Every morning:
            {
              "action": "create",
              "workflow_id": "wf_123",
              "trigger_type": "scheduler",
              "name": "Every morning",
              "enabled": true,
              "config_json": {"cron": "0 8 * * *", "timezone": "America/Chicago", "input": {}}
            }
            </scheduler_trigger>

            <webhook_trigger>
            Use webhook when an external system should start the workflow through HTTP.
            config_json:
            {
              "path": "/api/v1/workflow-webhooks/customer-summary",
              "inputMapping": {
                "customerId": "{{body.customer_id}}"
              },
              "auth": {"mode": "secret_header", "header": "X-Lightflare-Secret"}
            }
            </webhook_trigger>

            <rules>
            Requires workflow_id because this tool only manages triggers for an already-created workflow.
            If creating the workflow and trigger together, use create-workflow instead so no second call needs the returned workflow_id.
            </rules>
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-workflow-trigger")
            .description("Create, update, or delete manual, webhook, and scheduler triggers for an existing workflow.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("action", "string", "create, update, or delete.", true),
                    WorkflowToolDefinitions.input("workflow_id", "string", "Existing workflow id.", true),
                    WorkflowToolDefinitions.input("trigger_id", "string", "Trigger id for update/delete.", false),
                    WorkflowToolDefinitions.input("trigger_type", "string", "manual, webhook, or scheduler.", false),
                    WorkflowToolDefinitions.input("name", "string", "Trigger display name.", false),
                    WorkflowToolDefinitions.input("enabled", "boolean", "Whether the trigger is enabled.", false),
                    WorkflowToolDefinitions.input("config_json", "object", "Trigger config JSON object or JSON string.", false)
            ))
            .build();

    private final ManageWorkflowTool workflowOperations;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        try {
            return workflowOperations.manageTrigger(arguments);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
