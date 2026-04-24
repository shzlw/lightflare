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
public class UpdateApplicationTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("update-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("update-application")
            .description("Update an existing application's metadata.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("application_id", "string", "Application id to update.", true),
                    ApplicationToolDefinitions.input("name", "string", "Updated application name.", false),
                    ApplicationToolDefinitions.input("description", "string", "Updated application description.", false),
                    ApplicationToolDefinitions.input("source_chat_session_id", "string", "Updated source chat session id.", false),
                    ApplicationToolDefinitions.input("published_version_id", "string", "Updated published version id.", false),
                    ApplicationToolDefinitions.input("triggers", "array", "Optional trigger definitions to create while updating.", false)
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
            return applicationOperations.updateApplication(arguments, context);
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
