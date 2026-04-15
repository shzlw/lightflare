package com.lightflare.server.tools.bravesearch;

public record BraveWebSearchRequest(
        String query,
        String country,
        String searchLang,
        String uiLang,
        Integer count,
        Integer offset,
        String freshness,
        String safesearch,
        Boolean extraSnippets
) {
}
