<purpose>
Use this tool for simple enable, activate, disable, pause, or stop-running requests for an existing workflow.
It changes workflow status without changing steps or triggers.
</purpose>

<arguments>
workflow_id:
  Required. Workflow to enable or disable.

enabled:
  Required. true activates the workflow. false disables it.
</arguments>

<examples>
Enable:
{"workflow_id": "wf_123", "enabled": true}

Disable:
{"workflow_id": "wf_123", "enabled": false}
</examples>

<rules>
Use update-workflow when steps, name, description, inputs, or definition_json must change.
Use manage-workflow-trigger when only a trigger should be enabled/disabled or changed.
Use delete-workflow only when the user explicitly asks to delete the workflow.
</rules>