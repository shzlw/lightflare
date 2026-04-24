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
public class RunApplicationTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("run-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("run-application")
            .description("Run an existing application now with optional input data.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("application_id", "string", "Application id to run.", true),
                    ApplicationToolDefinitions.input("version_id", "string", "Optional application version id to run.", false),
                    ApplicationToolDefinitions.input("trigger_id", "string", "Optional trigger id to run through.", false),
                    ApplicationToolDefinitions.input("input_data", "object", "Optional application input data.", false)
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
            return applicationOperations.runApplication(arguments, context);
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
