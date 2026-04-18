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
public class InspectWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
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
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("inspect-workflow")
            .description("Read-only workflow inspection for workflow details, run history, and step logs.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("action", "string", "list, get, runs, or run-steps.", true),
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id for get and runs.", false),
                    WorkflowToolDefinitions.input("execution_id", "string", "Workflow run id for run-steps.", false)
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
            String action = arguments == null ? null : arguments.stream()
                    .filter(argument -> "action".equals(argument.getName()))
                    .findFirst()
                    .map(ToolArgument::asString)
                    .orElse(null);
            if (action == null || action.isBlank()) {
                return ToolResult.failure("inspect-workflow failed: Missing required argument: action");
            }
            return switch (action.trim().toLowerCase()) {
                case "list" -> workflowOperations.listWorkflows();
                case "get" -> workflowOperations.getWorkflow(arguments);
                case "runs" -> workflowOperations.workflowRuns(arguments);
                case "run-steps" -> workflowOperations.workflowRunSteps(arguments);
                default -> ToolResult.failure("inspect-workflow failed: Unknown action: " + action);
            };
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
