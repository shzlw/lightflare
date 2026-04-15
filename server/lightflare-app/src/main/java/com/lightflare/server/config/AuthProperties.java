package com.lightflare.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lightflare.auth")
public class AuthProperties {

    private CookieProperties cookie = new CookieProperties();

    @Data
    public static class CookieProperties {

        private boolean secure = false;

        private String sameSite = "Lax";
    }
}
