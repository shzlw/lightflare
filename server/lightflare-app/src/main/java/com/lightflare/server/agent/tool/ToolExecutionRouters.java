package com.lightflare.server.agent.tool;

import com.lightflare.server.harness.core.tool.ToolExecutionRouter;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolResult;
import java.util.List;
import java.util.stream.Stream;

public final class ToolExecutionRouters {

    private ToolExecutionRouters() {
    }

    public static ToolExecutionRouter normal(ToolService toolService) {
        return new ToolExecutionRouter() {
            @Override
            public List<ToolDefinition> listTools() {
                return toolService.listTools();
            }

            @Override
            public ToolDefinition findDefinition(String toolName) {
                return toolService.findDefinition(toolName);
            }

            @Override
            public ToolResult execute(String toolName, List<ToolArgument> arguments, String userId) {
                return toolService.execute(toolName, arguments, userId);
            }
        };
    }

    public static ToolExecutionRouter combined(ToolService toolService, InternalToolService internalToolService) {
        return new ToolExecutionRouter() {
            @Override
            public List<ToolDefinition> listTools() {
                return Stream.concat(toolService.listTools().stream(), internalToolService.listTools().stream())
                        .toList();
            }

            @Override
            public ToolDefinition findDefinition(String toolName) {
                if (internalToolService.supports(toolName)) {
                    return internalToolService.findDefinition(toolName);
                }
                return toolService.findDefinition(toolName);
            }

            @Override
            public ToolResult execute(String toolName, List<ToolArgument> arguments, String userId) {
                if (internalToolService.supports(toolName)) {
                    return internalToolService.execute(toolName, arguments, userId);
                }
                return toolService.execute(toolName, arguments, userId);
            }
        };
    }
}
