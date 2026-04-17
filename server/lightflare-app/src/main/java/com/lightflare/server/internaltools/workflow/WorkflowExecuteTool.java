package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.agent.tool.InternalTool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.workflow.WorkflowEngine;
import com.lightflare.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowExecuteTool implements InternalTool {

    private final WorkflowEngine workflowEngine;

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("execute-workflow")
            .description("Triggers the execution of a defined workflow. Returns the execution ID.")
            .category("workflow")
            .integrationId("internal")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("workflow_id")
                            .type("string")
                            .description("The ID of the workflow to run.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("initial_data")
                            .type("object")
                            .description("Optional initial data emitted by the TRIGGER step.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("start_step_id")
                            .type("string")
                            .description("Optional explicit step id to start from.")
                            .required(false)
                            .build()
            ))
            .build();

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String workflowId = getRequiredStringArgument(arguments, "workflow_id");
        Map<String, Object> initialData = getOptionalObjectArgument(arguments, "initial_data", "initialData");
        String startStepId = getOptionalStringArgument(arguments, "start_step_id", "startStepId");
        try {
            String execId = workflowEngine.execute(workflowId, initialData != null ? initialData : Map.of(), startStepId);
            return ToolResult.success("Workflow execution started. executionId=" + execId);
        } catch (Exception e) {
            return ToolResult.failure("Failed to execute workflow: " + e.getMessage());
        }
    }

    private String getRequiredStringArgument(List<ToolArgument> arguments, String name) {
        return arguments.stream()
                .filter(arg -> name.equals(arg.getName()))
                .map(ToolArgument::asString)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing required argument: " + name));
    }

    private String getOptionalStringArgument(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            String value = arguments.stream()
                    .filter(arg -> name.equals(arg.getName()))
                    .map(ToolArgument::asString)
                    .findFirst()
                    .orElse(null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOptionalObjectArgument(List<ToolArgument> arguments, String... names) {
        for (String name : names) {
            ToolArgument argument = arguments.stream()
                    .filter(arg -> name.equals(arg.getName()))
                    .findFirst()
                    .orElse(null);
            if (argument == null || argument.getValue() == null) {
                continue;
            }
            if (argument.getValue() instanceof Map<?, ?> mapValue) {
                return (Map<String, Object>) mapValue;
            }
            if (argument.getValue() instanceof String stringValue && !stringValue.isBlank()) {
                Object parsed = JsonUtils.fromJson(stringValue);
                if (parsed instanceof Map<?, ?> mapValue) {
                    return (Map<String, Object>) mapValue;
                }
            }
        }
        return null;
    }
}
