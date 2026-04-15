package com.lightflare.server.tools.openmeteo;

import com.lightflare.server.tools.core.ToolSelection;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "lightflare.tools.openmeteo")
public record OpenMeteoProperties(
        boolean enabled,
        List<String> enabledTools,
        String geocodingBaseUrl,
        String forecastBaseUrl,
        int defaultForecastDays
) implements ToolSelection {

    public OpenMeteoProperties {
        enabledTools = enabledTools == null ? List.of() : List.copyOf(enabledTools);
        geocodingBaseUrl = defaultIfBlank(geocodingBaseUrl, "https://geocoding-api.open-meteo.com");
        forecastBaseUrl = defaultIfBlank(forecastBaseUrl, "https://api.open-meteo.com");
        defaultForecastDays = defaultForecastDays > 0 ? defaultForecastDays : 3;
    }

    @Override
    public String integrationId() {
        return "openmeteo";
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
