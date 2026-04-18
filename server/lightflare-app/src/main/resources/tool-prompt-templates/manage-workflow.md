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
  Delete the workflow and related triggers, runs, and step logs.

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
Use Spring cron with 6 fields: second minute hour day-of-month month day-of-week.
Every minute is "0 * * * * *". Every morning at 8am is "0 0 8 * * *".
Use enabled=false unless the user explicitly asks to enable, activate, or start the schedule now.

Required config_json fields:
  - cron: Spring cron expression
  - timezone: IANA timezone, for example America/Chicago

Optional config_json fields:
  - input: object passed as workflow input
  - nextRunAt: server may compute this
  - lastStartedAt, lastCompletedAt, lastSuccessAt, lastFailureAt, lastError: server-maintained runtime metadata

config_json example:
{
  "cron": "0 0 8 * * *",
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
      "enabled": false,
      "config_json": {
        "cron": "0 0 8 * * *",
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
      "config_json": {
        "cron": "0 0 8 * * *",
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
Use status=active only when the user explicitly asks to enable, activate, or start it now.
For scheduled workflows, keep status=draft and scheduler enabled=false unless activation is explicit.
Use action=enable with enabled=false to disable a workflow.
Do not delete workflows unless the user clearly asks to delete them.
When updating a workflow, preserve existing ids, inputs, triggers, and steps unless the user asks for a replacement.
If required details are missing, ask a concise follow-up question before creating an active scheduler or webhook trigger.
</status_and_safety>