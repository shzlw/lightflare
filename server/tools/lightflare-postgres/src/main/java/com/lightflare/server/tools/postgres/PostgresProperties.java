package com.lightflare.server.tools.postgres;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lightflare.tools.postgres")
public class PostgresProperties {

    private boolean enabled = false;
    private Map<String, Connection> connections = new HashMap<>();

    @Data
    public static class Connection {
        private String connectionUrl;
        private String username;
        private String password;
    }
}
