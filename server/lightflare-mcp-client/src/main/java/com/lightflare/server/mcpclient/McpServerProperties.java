package com.lightflare.server.mcpclient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lightflare.mcp")
public class McpServerProperties {

    private final Map<String, Server> servers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Server {

        private boolean enabled = true;

        private McpTransportType transport = McpTransportType.STDIO;

        private String command;

        private final List<String> args = new ArrayList<>();

        private final Map<String, String> env = new LinkedHashMap<>();

        private String url;

        private String endpoint;

        private String sseEndpoint;
    }
}
