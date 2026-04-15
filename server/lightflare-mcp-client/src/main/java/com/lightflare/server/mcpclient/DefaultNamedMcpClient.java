package com.lightflare.server.mcpclient;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DefaultNamedMcpClient implements NamedMcpClient {

    private final String name;

    private final McpSyncClient delegate;

    @Override
    public String name() {
        return name;
    }

    @Override
    public McpSchema.ListToolsResult listTools() {
        return delegate.listTools();
    }

    @Override
    public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
        return delegate.callTool(request);
    }

    @Override
    public void close() {
        delegate.closeGracefully();
    }
}
