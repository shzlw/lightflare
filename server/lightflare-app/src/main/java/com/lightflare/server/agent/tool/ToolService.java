package com.lightflare.server.agent.tool;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final McpToolService mcpToolService;

    public List<ToolDefinition> listTools() {
        List<Tool> registeredTools = List.copyOf(toolRegistry.list());
        List<ToolDefinition> localToolDefinitions = registeredTools.stream()
                .map(Tool::definition)
                .toList();
        List<ToolDefinition> mcpToolDefinitions = mcpToolService.listTools();
        List<ToolDefinition> toolDefinitions = java.util.stream.Stream.concat(
                        localToolDefinitions.stream(),
                        mcpToolDefinitions.stream()
                )
                .toList();
        log.info("Registered tools ({}): {}",
                registeredTools.size(),
                registeredTools.stream()
                        .map(Tool::definition)
                        .map(ToolDefinition::getName)
                        .collect(Collectors.joining(", ")));
        log.info("Discovered MCP tools ({}): {}",
                mcpToolDefinitions.size(),
                mcpToolDefinitions.stream()
                        .map(ToolDefinition::getName)
                        .collect(Collectors.joining(", ")));
        log.info("Flattened callable tools ({}): {}",
                toolDefinitions.size(),
                toolDefinitions.stream()
                        .map(ToolDefinition::getName)
                        .collect(Collectors.joining(", ")));
        return toolDefinitions;
    }

    public ToolResult execute(String toolName, List<ToolArgument> arguments) {
        return execute(toolName, arguments, null);
    }

    public ToolResult execute(String toolName, List<ToolArgument> arguments, String userId) {
        log.info("-----------------------");
        log.info("Executing toolName={}, argumentCount={}",
                toolName, arguments != null ? arguments.size() : 0);
        log.info("Tool arguments: {}", arguments);
        ToolResult result;
        Tool localTool = toolRegistry.find(toolName).orElse(null);
        if (localTool != null) {
            ToolExecutionContext context = new ToolExecutionContext(localTool.definition(), userId);
            result = localTool.execute(arguments, context);
        } else if (mcpToolService.supports(toolName)) {
            result = mcpToolService.execute(toolName, arguments);
        } else {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        log.info("Executed toolName={}, success={}",
                toolName, result != null && result.success());
        log.info("-----------------------");
        return result;
    }

    public ToolDefinition findDefinition(String toolName) {
        Tool tool = toolRegistry.find(toolName).orElse(null);
        if (tool != null) {
            return tool.definition();
        }
        return mcpToolService.findDefinition(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
    }
}
