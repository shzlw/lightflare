package com.lightflare.server.agent.tool;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolResult;
import java.util.List;

public interface ToolExecutionRouter {

    List<ToolDefinition> listTools();

    ToolDefinition findDefinition(String toolName);

    ToolResult execute(String toolName, List<ToolArgument> arguments, String userId);
}
