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
public class CreateWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("create-workflow.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("create-workflow")
            .description("Create a new workflow, optionally with manual, webhook, or scheduler triggers in the same call.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("name", "string", "Workflow name.", true),
                    WorkflowToolDefinitions.input("description", "string", "Workflow description.", false),
                    WorkflowToolDefinitions.input("status", "string", "draft, active, or disabled. Use draft unless the user explicitly asks to enable/activate/start now.", false),
                    WorkflowToolDefinitions.input("definition_json", "object", "Workflow definition JSON object or JSON string.", true),
                    WorkflowToolDefinitions.input("triggers", "array", "Optional trigger definitions to create with the workflow.", false)
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
            return workflowOperations.createWorkflow(arguments, context);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
