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
public class ManageApplicationTriggerTool implements InternalTool {

    private static final String USAGE_GUIDANCE = FileUtils.loadToolPromptTemplate("manage-application-trigger.md");

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("manage-application-trigger")
            .description("Create, update, or delete manual, webhook, and cron triggers for an existing application version.")
            .category("application")
            .integrationId("internal")
            .usageGuidance(USAGE_GUIDANCE)
            .properties(List.of(
                    ApplicationToolDefinitions.input("action", "string", "create, update, or delete.", true),
                    ApplicationToolDefinitions.input("application_id", "string", "Existing application id.", true),
                    ApplicationToolDefinitions.input("version_id", "string", "Existing application version id.", true),
                    ApplicationToolDefinitions.input("trigger_id", "string", "Trigger id for update/delete.", false),
                    ApplicationToolDefinitions.input("trigger_type", "string", "manual, webhook, or cron.", false),
                    ApplicationToolDefinitions.input("start_step_id", "string", "Start step id for the trigger.", false),
                    ApplicationToolDefinitions.input("config_json", "object", "Trigger config JSON object or JSON string.", false)
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
            return applicationOperations.manageTrigger(arguments);
        } catch (Exception e) {
            return ApplicationToolDefinitions.failure(DEFINITION.getName(), e);
        }
    }
}
