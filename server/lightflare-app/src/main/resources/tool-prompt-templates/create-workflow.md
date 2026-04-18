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
      "id": "geocode_location",
      "name": "Geocode ZIP code",
      "type": "tool",
      "toolName": "geocoding",
      "input": {"location": "{{inputs.zip}}"},
      "onError": "stop"
    },
    {
      "id": "get_weather",
      "name": "Get weather forecast",
      "type": "tool",
      "toolName": "weather-forecast",
      "input": {
        "latitude": "{{steps.geocode_location.output.latitude}}",
        "longitude": "{{steps.geocode_location.output.longitude}}",
        "forecast_days": 1
      },
      "onError": "stop"
    },
    {
      "id": "summarize_weather",
      "name": "Summarize weather",
      "type": "llm",
      "prompt": "Summarize this weather forecast for ZIP code {{inputs.zip}}: {{steps.get_weather.output}}",
      "onError": "stop"
    }
  ]
}

Keep workflows simple and ordered. Use stable lowercase step ids with underscores.
Do not drop user-provided constants. Put them in `inputs[].default`, manual trigger `inputFields[].default`, scheduler trigger `config_json.input`, or directly in step input when the workflow should always use that value.
</workflow_definition_schema>

<step_types>
llm:
  Use for reasoning, summarizing, drafting, classification, decisions, or formatting prior tool output.
  Use llm only when the workflow needs reasoning, summarization, transformation, classification, or when the exact runtime tool name/input contract is unknown.
  Do not use an llm step just to call a known tool.

tool:
  Prefer tool when the exact tool name and input contract are known from the available tool catalog.
  Required fields: id, name, type="tool", toolName, input.
  For tool-backed tasks, create a direct tool step first, then add a separate llm step only if the user also needs reasoning, summarization, drafting, or formatting of the tool output.
  Examples: use toolName="web-page-content-extractor" directly for fetching page content; use toolName="http-get" directly for fixed HTTP GET calls; use a qualified MCP tool name like "mcp.<server>.<tool>" when the target MCP tool is known.

condition:
  Use sparingly for simple branch/skip metadata. Prefer linear steps unless the user explicitly asks for if/else behavior.
</step_types>

<tool_first_policy>
Prefer deterministic workflow steps over agentic ones.
If the user request maps to a known tool, generate `type="tool"` with `toolName` and explicit `input` instead of an `llm` prompt that asks the model to use tools.
Use `type="llm"` after a tool step when the workflow must interpret, summarize, or transform the direct tool output.
Use `type="llm"` alone only when no exact tool name/input contract is available or the task is primarily reasoning/generation.
</tool_first_policy>

<template_variables>
Workflow inputs and previous step outputs can be passed between steps with `{{...}}`.
Use `{{inputs.name}}` to read workflow input values.
Use `{{steps.step_id.output}}` to pass the full parsed output from a previous step.
Use `{{steps.step_id.output.field}}` or deeper paths like `{{steps.step_id.output.content.title}}` to read a specific output field.
When a tool input value is exactly one template, such as `"payload": "{{steps.fetch_page.output}}"`, the engine passes the parsed object/array/value directly to the tool.
When a template appears inside surrounding text, such as `"prompt": "Summarize {{steps.fetch_page.output}}"`, the engine renders it as text, using JSON for objects and arrays.
Prefer step ids with lowercase letters, numbers, and underscores so template references are simple and stable.
Do not invent output mappings. Reference prior step outputs directly with `{{steps.<id>.output...}}`.
</template_variables>

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
        "id": "geocode_location",
        "name": "Geocode ZIP code",
        "type": "tool",
        "toolName": "geocoding",
        "input": {
          "location": "{{inputs.zip}}"
        },
        "onError": "stop"
      },
      {
        "id": "get_weather",
        "name": "Get current weather",
        "type": "tool",
        "toolName": "weather-forecast",
        "input": {
          "latitude": "{{steps.geocode_location.output.latitude}}",
          "longitude": "{{steps.geocode_location.output.longitude}}",
          "forecast_days": 1
        },
        "onError": "stop"
      },
      {
        "id": "summarize_weather",
        "name": "Summarize current weather",
        "type": "llm",
        "prompt": "Return temperature, conditions, and a short summary for ZIP code {{inputs.zip}} from this forecast output: {{steps.get_weather.output}}",
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
        "id": "fetch_hacker_news",
        "name": "Fetch Hacker News",
        "type": "tool",
        "toolName": "web-page-content-extractor",
        "input": {
          "url": "https://news.ycombinator.com/"
        },
        "onError": "stop"
      },
      {
        "id": "summarize_hacker_news",
        "name": "Summarize top Hacker News stories",
        "type": "llm",
        "prompt": "From this Hacker News page content, identify the top 3 stories and summarize them concisely: {{steps.fetch_hacker_news.output}}",
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
