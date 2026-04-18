<purpose>
Use this tool when the user wants to execute an existing workflow now.
It creates a workflow run and returns executionId. Step logs can be viewed with inspect-workflow action="run-steps".
</purpose>

<arguments>
workflow_id:
  Required. Existing workflow to run.

trigger_id:
  Optional. Use when the user specifically runs through an existing manual/webhook/scheduler trigger.
  If omitted, the run is manual.

input_data:
  Optional object passed into workflow inputs.
</arguments>

<examples>
Manual run with input:
{
  "workflow_id": "wf_123",
  "input_data": {"zip": "75036"}
}

Run through a specific trigger:
{
  "workflow_id": "wf_123",
  "trigger_id": "trig_456",
  "input_data": {"customerId": "cust_001"}
}
</examples>

<rules>
Use create-workflow if the user is asking to create a workflow.
Use inspect-workflow action="runs" or action="run-steps" to view results/logs after a run.
</rules>