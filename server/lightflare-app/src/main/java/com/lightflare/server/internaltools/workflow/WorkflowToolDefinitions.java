package com.lightflare.server.internaltools.workflow;

import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;

final class WorkflowToolDefinitions {

    private WorkflowToolDefinitions() {
    }

    static ToolInputDefinition input(String name, String type, String description, boolean required) {
        return ToolInputDefinition.builder()
                .name(name)
                .type(type)
                .description(description)
                .required(required)
                .build();
    }

    static ToolResult failure(String toolName, Exception e) {
        return ToolResult.failure(toolName + " failed: " + e.getMessage());
    }
}
