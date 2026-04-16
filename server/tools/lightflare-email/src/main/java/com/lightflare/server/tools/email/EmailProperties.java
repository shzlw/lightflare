package com.lightflare.server.tools.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lightflare.tools.email")
public class EmailProperties {

    private boolean enabled;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String from;
    private Map<String, String> properties = new HashMap<>();
}
