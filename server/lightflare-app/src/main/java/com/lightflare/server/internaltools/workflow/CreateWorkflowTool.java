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
public class CreateWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
            <purpose>
            Use this tool only when the user wants to create a new workflow or draft workflow.
            A workflow is the executable plan. Triggers are how it starts: manual, webhook, or scheduler.
            If the user asks for a recurring/scheduled job, still create a workflow here and include a scheduler trigger in `triggers`.
            Do not create standalone scheduler jobs.
            </purpose>

            <when_to_use>
            Use create-workflow for requests like:
            - "create a workflow..."
            - "make a draft workflow..."
            - "every morning, summarize my tasks"
            - "create a scheduled task to..."
            - "create a webhook workflow..."
            - "build a manual workflow where I enter fields and click run"

            If the user wants to modify an existing workflow, use update-workflow instead.
            If the user wants to run an existing workflow, use run-workflow instead.
            </when_to_use>

            <arguments>
            name:
              Required. Short workflow name.

            description:
              Optional. One sentence describing what the workflow does.

            status:
              Optional. Use "draft" unless the user clearly asks to enable/activate it now.
              For scheduled workflows, still use "draft" unless the user explicitly asks to enable, activate, or start the schedule now.

            definition_json:
              Required. JSON object or JSON string using the workflow definition schema below.
              If the user provides concrete values such as "zip = 75036", capture them as input defaults and reference them from steps with {{inputs.<name>}}.

            triggers:
              Optional array. Include trigger definitions when the user asks for manual fields, webhook start, or scheduler/recurring execution in the same create request.
              Each trigger item supports trigger_type, name, enabled, config_json.
              If omitted, the system creates a default enabled manual trigger using definition_json.inputs as inputFields.
            </arguments>

            <workflow_definition_schema>
            Preferred definition_json shape:
            {
              "version": 1,
              "inputs": [
                {
                  "name": "zip",
                  "label": "ZIP code",
                  "type": "string",
                  "required": true,
                  "description": "ZIP code used by the workflow."
                }
              ],
              "steps": [
                {
                  "id": "summarize_weather",
                  "name": "Summarize weather",
                  "type": "llm",
                  "prompt": "Get the current weather for ZIP code {{inputs.zip}} using available tools if possible, then return temperature, conditions, and a short summary.",
                  "input": {"zip": "{{inputs.zip}}"},
                  "onError": "stop"
                }
              ]
            }

            Keep workflows simple and ordered. Use stable lowercase step ids with underscores.
            Do not drop user-provided constants. Put them in `inputs[].default`, manual trigger `inputFields[].default`, scheduler trigger `config_json.input`, or directly in step input when the workflow should always use that value.
            </workflow_definition_schema>

            <step_types>
            llm:
              Use for reasoning, summarizing, drafting, classification, decisions, or best-effort tool use at runtime.
              Prefer llm when the exact runtime tool name is unknown.

            tool:
              Use only when the exact tool name and input contract are known.
              Required fields: id, name, type="tool", toolName, input.

            condition:
              Use sparingly for simple branch/skip metadata. Prefer linear steps unless the user explicitly asks for if/else behavior.
            </step_types>

            <trigger_structures>
            Manual trigger:
            {
              "trigger_type": "manual",
              "name": "Run manually",
              "enabled": true,
              "config_json": {
                "inputFields": [
                  {"name": "zip", "label": "ZIP code", "type": "string", "required": true}
                ]
              }
            }

            Scheduler trigger:
            Use Spring cron with 6 fields: second minute hour day-of-month month day-of-week.
            Every minute is "0 * * * * *". Do not use "* * * * *" in examples.
            {
              "trigger_type": "scheduler",
              "name": "Every minute",
              "enabled": false,
              "config_json": {
                "cron": "0 * * * * *",
                "timezone": "America/Chicago",
                "input": {"zip": "75036"}
              }
            }

            Webhook trigger:
            {
              "trigger_type": "webhook",
              "name": "Inbound webhook",
              "enabled": true,
              "config_json": {
                "path": "/api/v1/workflow-webhooks/current-weather",
                "inputMapping": {
                  "zip": "{{body.zip}}"
                },
                "auth": {"mode": "secret_header", "header": "X-Lightflare-Secret"}
              }
            }
            </trigger_structures>

            <complete_examples>
            User: "create a draft workflow called test"
            Tool call:
            {
              "name": "test",
              "status": "draft",
              "definition_json": {
                "version": 1,
                "inputs": [],
                "steps": []
              }
            }

            User: "Every morning, summarize my tasks."
            Tool call:
            {
              "name": "Daily task summary",
              "description": "Summarize open tasks every morning.",
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
                  "enabled": false,
                  "config_json": {"cron": "0 0 8 * * *", "timezone": "America/Chicago", "input": {}}
                }
              ]
            }

            User: "Create a new workflow to get the current weather for zip = 75036"
            Tool call:
            {
              "name": "Current weather for 75036",
              "description": "Get and summarize the current weather for ZIP code 75036.",
              "status": "draft",
              "definition_json": {
                "version": 1,
                "inputs": [
                  {"name": "zip", "label": "ZIP code", "type": "string", "required": true, "default": "75036"}
                ],
                "steps": [
                  {
                    "id": "get_weather",
                    "name": "Get current weather",
                    "type": "llm",
                    "prompt": "Get the current weather for ZIP code {{inputs.zip}} using available tools if possible, then return temperature, conditions, and a short summary.",
                    "input": {"zip": "{{inputs.zip}}"},
                    "onError": "stop"
                  }
                ]
              },
              "triggers": [
                {
                  "trigger_type": "manual",
                  "name": "Run manually",
                  "enabled": true,
                  "config_json": {
                    "inputFields": [
                      {"name": "zip", "label": "ZIP code", "type": "string", "required": true, "default": "75036"}
                    ]
                  }
                }
              ]
            }

            User: "Create a draft workflow called test. Trigger it with a schedule runs every minute. Go to https://news.ycombinator.com/ to get the top 3 news and summarize them."
            Tool call:
            {
              "name": "test",
              "description": "Summarize the top 3 Hacker News stories every minute.",
              "status": "draft",
              "definition_json": {
                "version": 1,
                "inputs": [],
                "steps": [
                  {
                    "id": "summarize_hacker_news",
                    "name": "Summarize top Hacker News stories",
                    "type": "llm",
                    "prompt": "Go to https://news.ycombinator.com/, get the top 3 stories, and summarize them concisely.",
                    "onError": "stop"
                  }
                ]
              },
              "triggers": [
                {
                  "trigger_type": "scheduler",
                  "name": "Every minute",
                  "enabled": false,
                  "config_json": {"cron": "0 * * * * *", "timezone": "America/Chicago", "input": {}}
                }
              ]
            }
            </complete_examples>

            <rules>
            Create scheduler, manual, and webhook triggers in the same create-workflow call when the user includes trigger intent.
            Preserve values from the user's request. For "zip = 75036", include "default": "75036" on the workflow input and manual trigger input field. For scheduled workflows, also include {"zip": "75036"} in scheduler config_json.input.
            Use status="draft" and scheduler enabled=false unless the user explicitly asks to enable, activate, or start the workflow now.
            Create only one scheduler trigger for one schedule. Do not create multiple scheduler triggers for the same cron/timezone/input.
            If the user does not specify any trigger, it is acceptable to omit `triggers`; a default manual trigger will be created automatically.
            Do not call manage-workflow or scheduler tools.
            Do not require workflow_id for triggers included during create.
            Ask a follow-up only when a required trigger detail cannot be reasonably inferred.
            </rules>
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("create-workflow")
            .description("Create a new workflow, optionally with manual, webhook, or scheduler triggers in the same call.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("name", "string", "Workflow name.", true),
                    WorkflowToolDefinitions.input("description", "string", "Workflow description.", false),
                    WorkflowToolDefinitions.input("status", "string", "draft, active, or disabled. Use draft unless the user explicitly asks to enable/activate/start now.", false),
                    WorkflowToolDefinitions.input("definition_json", "object", "Workflow definition JSON object or JSON string.", true),
                    WorkflowToolDefinitions.input("triggers", "array", "Optional trigger definitions to create with the workflow.", false)
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
            return workflowOperations.createWorkflow(arguments, context);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
