<purpose>
Use this tool only when the user clearly asks to delete/remove an existing workflow.
Deleting a workflow also removes its triggers, runs, and step logs through application cleanup logic.
</purpose>

<arguments>
workflow_id:
  Required. Workflow to delete.
</arguments>

<examples>
{
  "workflow_id": "wf_123"
}
</examples>

<rules>
Do not use for disable/pause requests; use enable-workflow with enabled=false.
Do not delete based on ambiguous wording like "stop running" unless the user clearly asks for deletion.
</rules>