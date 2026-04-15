package com.lightflare.server.mcpclient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpClientConfig {

    private static final Duration MCP_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration MCP_INITIALIZATION_TIMEOUT = Duration.ofSeconds(10);

    private static final McpSchema.Implementation CLIENT_INFO = new McpSchema.Implementation("lightflare", "1.0");

    @Bean
    List<NamedMcpClient> namedMcpClients(McpServerProperties properties) {
        List<NamedMcpClient> clients = new ArrayList<>();
        for (Map.Entry<String, McpServerProperties.Server> entry : properties.getServers().entrySet()) {
            String serverName = entry.getKey();
            McpServerProperties.Server server = entry.getValue();
            if (!server.isEnabled()) {
                continue;
            }
            clients.add(buildClient(serverName, server));
        }
        return List.copyOf(clients);
    }

    private McpClientTransport buildTransport(String serverName, McpServerProperties.Server server) {
        return switch (server.getTransport()) {
            case STDIO -> buildStdioTransport(serverName, server);
            case SSE -> buildSseTransport(serverName, server);
            case STREAMABLE_HTTP -> buildStreamableHttpTransport(serverName, server);
        };
    }

    private McpClientTransport buildStdioTransport(String serverName, McpServerProperties.Server server) {
        Assert.hasText(server.getCommand(), "MCP stdio server '" + serverName + "' requires a command");
        ServerParameters parameters = ServerParameters.builder(server.getCommand())
            .args(server.getArgs())
            .env(server.getEnv())
            .build();
        return new StdioClientTransport(parameters, jsonMapper());
    }

    private McpClientTransport buildSseTransport(String serverName, McpServerProperties.Server server) {
        Assert.hasText(server.getUrl(), "MCP SSE server '" + serverName + "' requires a url");
        String sseEndpoint = StringUtils.hasText(server.getSseEndpoint()) ? server.getSseEndpoint() : "/sse";
        return HttpClientSseClientTransport.builder(server.getUrl())
            .sseEndpoint(sseEndpoint)
            .build();
    }

    private McpClientTransport buildStreamableHttpTransport(String serverName, McpServerProperties.Server server) {
        Assert.hasText(server.getUrl(), "MCP streamable-http server '" + serverName + "' requires a url");
        String endpoint = StringUtils.hasText(server.getEndpoint()) ? server.getEndpoint() : "/mcp";
        return HttpClientStreamableHttpTransport.builder(server.getUrl())
            .endpoint(endpoint)
            .build();
    }

    private McpJsonMapper jsonMapper() {
        return new JacksonMcpJsonMapper(JsonMapper.shared());
    }

    private NamedMcpClient buildClient(String serverName, McpServerProperties.Server server) {
        try {
            McpSyncClient client = McpClient.sync(buildTransport(serverName, server))
                .requestTimeout(MCP_REQUEST_TIMEOUT)
                .initializationTimeout(MCP_INITIALIZATION_TIMEOUT)
                .clientInfo(CLIENT_INFO)
                .build();
            client.initialize();
            return new DefaultNamedMcpClient(serverName, client);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "Failed to initialize MCP server '%s' (%s) %s".formatted(
                    serverName,
                    server.getTransport(),
                    serverTargetDescription(server)
                ),
                e
            );
        }
    }

    private String serverTargetDescription(McpServerProperties.Server server) {
        return switch (server.getTransport()) {
            case STDIO -> "using command '%s'".formatted(server.getCommand());
            case SSE, STREAMABLE_HTTP -> "at '%s'".formatted(server.getUrl());
        };
    }
}
