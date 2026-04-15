package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.bootstrap")
public class BootstrapProperties {

    private SuperAdminProperties superadmin = new SuperAdminProperties();

    @Data
    public static class SuperAdminProperties {

        private String username;

        private String email;

        private String password;
    }
}
