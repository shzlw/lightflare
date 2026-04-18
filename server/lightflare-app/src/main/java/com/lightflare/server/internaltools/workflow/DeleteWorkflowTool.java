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
public class DeleteWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = """
            <purpose>
            Use this tool only when the user clearly asks to delete/remove an existing workflow.
            Deleting a workflow also removes its triggers and run records through database cascade behavior.
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
            """;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("delete-workflow")
            .description("Delete an existing workflow.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id to delete.", true)
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
            return workflowOperations.deleteWorkflow(arguments);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
