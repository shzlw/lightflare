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
public class InspectWorkflowTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("inspect-workflow.md");

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
