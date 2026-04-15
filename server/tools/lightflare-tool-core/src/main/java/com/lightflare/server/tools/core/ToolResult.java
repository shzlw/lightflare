package com.lightflare.server.tools.core;

public record ToolResult(
        boolean success,
        String content
) {

    public static ToolResult success(String content) {
        return new ToolResult(true, content);
    }

    public static ToolResult failure(String content) {
        return new ToolResult(false, content);
    }
}
