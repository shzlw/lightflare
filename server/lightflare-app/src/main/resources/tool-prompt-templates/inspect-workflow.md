<purpose>
Use this read-only tool to inspect workflows, workflow triggers, workflow runs, and step execution logs.
It does not create, update, run, enable, disable, or delete anything.
</purpose>

<actions>
list:
  List workflows. No workflow_id required.

get:
  Required: workflow_id.
  Returns the workflow, triggers, and recent runs. Use before updating when the full current definition is not in context.

runs:
  Required: workflow_id.
  Returns recent workflow runs.

run-steps:
  Required: execution_id.
  Returns execution logs for each step in a workflow run.
</actions>

<examples>
List workflows:
{"action": "list"}

Inspect one workflow before editing:
{"action": "get", "workflow_id": "wf_123"}

View run history:
{"action": "runs", "workflow_id": "wf_123"}

View step logs:
{"action": "run-steps", "execution_id": "run_123"}
</examples>

<rules>
Use inspect-workflow before update-workflow when the user says "this workflow" and the current definition is not available.
Use run-workflow when the user wants to execute a workflow.
</rules>