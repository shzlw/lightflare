package com.lightflare.server.internaltools.application;

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
public class InspectApplicationTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("inspect-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("inspect-application")
            .description("Read-only application inspection for application details, run history, and step logs.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("action", "string", "list, get, runs, or run-steps.", true),
                    ApplicationToolDefinitions.input("application_id", "string", "Application id for get and runs.", false),
                    ApplicationToolDefinitions.input("execution_id", "string", "Application run id for run-steps.", false)
            ))
            .build();

    private final ManageApplicationTool applicationOperations;

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
                return ToolResult.failure("inspect-application failed: Missing required argument: action");
            }
            return switch (action.trim().toLowerCase()) {
                case "list" -> applicationOperations.listApplications();
                case "get" -> applicationOperations.getApplication(arguments);
                case "runs" -> applicationOperations.applicationRuns(arguments);
                case "run-steps" -> applicationOperations.applicationRunSteps(arguments);
                default -> ToolResult.failure("inspect-application failed: Unknown action: " + action);
            };
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
