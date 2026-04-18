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
public class EnableWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
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
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("enable-workflow")
            .description("Enable or disable an existing workflow.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id to enable or disable.", true),
                    WorkflowToolDefinitions.input("enabled", "boolean", "true to enable, false to disable.", true)
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
            return workflowOperations.enableWorkflow(arguments);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
