package com.lightflare.server.mcpclient;

import io.modelcontextprotocol.spec.McpSchema;

public interface NamedMcpClient {

    String name();

    McpSchema.ListToolsResult listTools();

    McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request);

    default void close() {
    }
}
