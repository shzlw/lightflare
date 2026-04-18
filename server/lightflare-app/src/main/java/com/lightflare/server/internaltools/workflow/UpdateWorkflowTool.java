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
public class UpdateWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("update-workflow.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("update-workflow")
            .description("Update an existing workflow definition or metadata.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("workflow_id", "string", "Workflow id to update.", true),
                    WorkflowToolDefinitions.input("name", "string", "Updated workflow name.", false),
                    WorkflowToolDefinitions.input("description", "string", "Updated workflow description.", false),
                    WorkflowToolDefinitions.input("status", "string", "draft, active, or disabled.", false),
                    WorkflowToolDefinitions.input("definition_json", "object", "Full updated workflow definition JSON object or JSON string.", false),
                    WorkflowToolDefinitions.input("triggers", "array", "Optional trigger definitions to create while updating.", false)
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
            return workflowOperations.updateWorkflow(arguments, context);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
