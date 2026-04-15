package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolSelection;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "lightflare.tools.httpclient")
public record HttpClientProperties(
        boolean enabled,
        List<String> enabledTools,
        Duration connectTimeout,
        Duration readTimeout,
        boolean allowPrivateNetwork,
        List<String> allowedHosts
) implements ToolSelection {

    public HttpClientProperties {
        enabledTools = enabledTools == null ? List.of() : List.copyOf(enabledTools);
        connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(5));
        readTimeout = positiveOrDefault(readTimeout, Duration.ofSeconds(30));
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .filter(host -> host != null && !host.isBlank())
                .map(host -> host.trim().toLowerCase())
                .toList();
    }

    @Override
    public String integrationId() {
        return "httpclient";
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
