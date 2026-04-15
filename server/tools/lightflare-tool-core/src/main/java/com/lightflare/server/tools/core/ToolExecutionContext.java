package com.lightflare.server.tools.core;

import java.util.Objects;

public record ToolExecutionContext(ToolDefinition tool, String userId) {

    public ToolExecutionContext {
        tool = Objects.requireNonNull(tool, "tool must not be null");
    }
}
