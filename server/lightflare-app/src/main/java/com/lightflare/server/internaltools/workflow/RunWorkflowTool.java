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
public class RunWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
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
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("run-workflow")
            .description("Run an existing workflow now with optional input data.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id to run.", true),
                    WorkflowToolDefinitions.input("trigger_id", "string", "Optional trigger id to run through.", false),
                    WorkflowToolDefinitions.input("input_data", "object", "Optional workflow input data.", false)
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
            return workflowOperations.runWorkflow(arguments, context);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
