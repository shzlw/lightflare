package com.lightflare.server.agent.tool;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import java.util.List;

public interface InternalTool {

    ToolDefinition definition();

    ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context);
}
