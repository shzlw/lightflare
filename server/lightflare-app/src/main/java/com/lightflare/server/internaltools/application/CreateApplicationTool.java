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
public class CreateApplicationTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("create-application.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("create-application")
            .description("Create a new application, optionally with triggers.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("name", "string", "Application name.", true),
                    ApplicationToolDefinitions.input("description", "string", "Application description.", false),
                    ApplicationToolDefinitions.input("source_chat_session_id", "string", "Optional source chat session id.", false),
                    ApplicationToolDefinitions.input("published_version_id", "string", "Optional published version id.", false),
                    ApplicationToolDefinitions.input("triggers", "array", "Optional trigger definitions to create with the application.", false)
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
            return applicationOperations.createApplication(arguments, context);
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
