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
public class ManageWorkflowTriggerTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("manage-workflow-trigger.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-workflow-trigger")
            .description("Create, update, or delete manual, webhook, and scheduler triggers for an existing workflow.")
            .category("workflow")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    WorkflowToolDefinitions.input("action", "string", "create, update, or delete.", true),
                    WorkflowToolDefinitions.input("workflow_id", "string", "Existing workflow id.", true),
                    WorkflowToolDefinitions.input("trigger_id", "string", "Trigger id for update/delete.", false),
                    WorkflowToolDefinitions.input("trigger_type", "string", "manual, webhook, or scheduler.", false),
                    WorkflowToolDefinitions.input("name", "string", "Trigger display name.", false),
                    WorkflowToolDefinitions.input("enabled", "boolean", "Whether the trigger is enabled.", false),
                    WorkflowToolDefinitions.input("config_json", "object", "Trigger config JSON object or JSON string.", false)
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
            return workflowOperations.manageTrigger(arguments);
        } catch (Exception e) {
            return WorkflowToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
