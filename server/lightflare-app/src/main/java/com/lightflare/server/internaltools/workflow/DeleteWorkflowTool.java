package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.FileUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("delete-workflow.md");

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
