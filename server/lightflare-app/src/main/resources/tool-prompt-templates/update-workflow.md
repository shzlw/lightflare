<purpose>
Use this tool when the user wants to change an existing workflow's name, description, status, steps, inputs, or full definition.
</purpose>

<required_context>
Requires workflow_id.
If the current workflow definition is not already in context, first use inspect-workflow with action="get" to retrieve the workflow, triggers, and recent runs.
Preserve existing step ids, input names, trigger intent, and unrelated fields unless the user asks to replace them.
</required_context>

<definition_updates>
Send the full updated `definition_json`, not a partial patch, when changing steps or inputs.
Preferred definition_json structure:
{
  "version": 1,
  "inputs": [],
  "steps": [
    {
      "id": "step_id",
      "name": "Step name",
      "type": "llm",
      "prompt": "Runtime instruction.",
      "onError": "stop"
    }
  ]
}
</definition_updates>

<tool_first_policy>
Prefer direct tool steps over llm steps that ask the model to use tools.
When the target tool name and input contract are known from the tool catalog, generate `type="tool"` with `toolName` and explicit `input`.
Use `type="llm"` only for reasoning, summarizing, drafting, classification, transformation, or when the exact tool cannot be selected at workflow creation time.
For tasks that need both data retrieval and summarization, use a direct `tool` step for retrieval followed by a separate `llm` step for summarization.
Preserve existing direct tool steps unless the user explicitly asks to make the workflow agentic.
</tool_first_policy>

<template_variables>
Use `{{inputs.name}}` for workflow inputs.
Use `{{steps.step_id.output}}` for the full parsed output from a previous step.
Use `{{steps.step_id.output.field}}` or deeper paths like `{{steps.step_id.output.content.title}}` for specific output fields.
When a tool input value is exactly one template, the workflow engine passes the parsed object/array/value directly to the tool.
When a template appears inside surrounding text, the workflow engine renders it as text, using JSON for objects and arrays.
Do not add output mapping fields unless an existing workflow already depends on them; prefer direct references to prior step outputs.
</template_variables>

<examples>
User: "Add a final email step to this workflow."
Tool call:
{
  "workflow_id": "wf_123",
  "name": "Daily task summary",
  "description": "Summarize tasks and email the summary.",
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
</examples>

<rules>
Use create-workflow for new workflows.
Use manage-workflow-trigger for trigger-only changes.
Use enable-workflow for simple enable/disable requests.
</rules>
