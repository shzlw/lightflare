package com.lightflare.server.tools.bravesearch;

import com.lightflare.server.tools.core.ToolSelection;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "lightflare.tools.brave-search")
public record BraveSearchProperties(
        boolean enabled,
        List<String> enabledTools,
        String baseUrl,
        String apiKey,
        String country,
        String searchLang,
        String uiLang,
        String safesearch,
        int defaultCount,
        boolean extraSnippets
) implements ToolSelection {

    public BraveSearchProperties {
        enabledTools = enabledTools == null ? List.of() : List.copyOf(enabledTools);
        baseUrl = defaultIfBlank(baseUrl, "https://api.search.brave.com");
        country = defaultIfBlank(country, "US");
        searchLang = defaultIfBlank(searchLang, "en");
        uiLang = defaultIfBlank(uiLang, "en-US");
        safesearch = defaultIfBlank(safesearch, "moderate");
        defaultCount = defaultCount > 0 ? Math.min(defaultCount, 20) : 5;
    }

    @Override
    public String integrationId() {
        return "brave_search";
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
