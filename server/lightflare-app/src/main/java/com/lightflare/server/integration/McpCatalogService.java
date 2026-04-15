package com.lightflare.server.integration;

import com.lightflare.server.integration.McpResponse;
import com.lightflare.server.mcpclient.McpServerProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpCatalogService {

    private final McpServerProperties mcpServerProperties;

    public List<McpResponse> listMcps() {
        return mcpServerProperties.getServers().entrySet().stream()
                .map(entry -> {
                    McpServerProperties.Server server = entry.getValue();
                    return new McpResponse(
                            entry.getKey(),
                            server.getTransport() != null ? server.getTransport().name() : null,
                            server.getCommand(),
                            List.copyOf(server.getArgs()),
                            server.getEnv(),
                            server.getUrl(),
                            server.getEndpoint(),
                            server.getSseEndpoint()
                    );
                })
                .toList();
    }
}
