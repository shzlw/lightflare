package com.lightflare.server.mcpclient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class DefaultMcpClientRegistry implements McpClientRegistry {

    private final Map<String, NamedMcpClient> clientsByName;

    public DefaultMcpClientRegistry(List<NamedMcpClient> clients) {
        Map<String, NamedMcpClient> orderedClients = new LinkedHashMap<>();
        for (NamedMcpClient client : clients) {
            orderedClients.put(client.name(), client);
        }
        this.clientsByName = Map.copyOf(orderedClients);
    }

    @Override
    public List<NamedMcpClient> listClients() {
        return List.copyOf(clientsByName.values());
    }

    @PreDestroy
    void closeClients() {
        for (NamedMcpClient client : clientsByName.values()) {
            client.close();
        }
    }
}
