package com.lightflare.server.tools.core;

import java.util.List;

public interface Tool {

    ToolDefinition definition();

    ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context);
}
