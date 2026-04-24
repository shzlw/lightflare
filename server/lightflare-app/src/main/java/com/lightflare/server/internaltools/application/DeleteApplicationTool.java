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
public class DeleteApplicationTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("delete-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("delete-application")
            .description("Delete an existing application.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("application_id", "string", "Application id to delete.", true)
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
            return applicationOperations.deleteApplication(arguments);
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
