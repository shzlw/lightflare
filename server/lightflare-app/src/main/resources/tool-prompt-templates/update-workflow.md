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