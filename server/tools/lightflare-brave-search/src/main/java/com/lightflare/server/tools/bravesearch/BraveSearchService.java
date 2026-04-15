package com.lightflare.server.tools.bravesearch;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@RequiredArgsConstructor
public class BraveSearchService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_COUNT = 20;
    private static final int MAX_OFFSET = 9;

    private final RestClient restClient;
    private final BraveSearchProperties properties;

    public String search(BraveWebSearchRequest request) {
        BraveWebSearchRequest normalizedRequest = normalize(request);
        if (!StringUtils.hasText(normalizedRequest.query())) {
            throw new IllegalArgumentException("query is required");
        }

        BraveSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/res/v1/web/search")
                        .queryParam("q", normalizedRequest.query())
                        .queryParam("country", normalizedRequest.country())
                        .queryParam("search_lang", normalizedRequest.searchLang())
                        .queryParam("ui_lang", normalizedRequest.uiLang())
                        .queryParam("count", normalizedRequest.count())
                        .queryParam("offset", normalizedRequest.offset())
                        .queryParam("safesearch", normalizedRequest.safesearch())
                        .queryParam("result_filter", "web")
                        .queryParam("extra_snippets", normalizedRequest.extraSnippets())
                        .queryParamIfPresent("freshness", optionalText(normalizedRequest.freshness()))
                        .build())
                .retrieve()
                .body(BraveSearchResponse.class);

        return toJson(toOutput(response, normalizedRequest.query()));
    }

    private BraveWebSearchRequest normalize(BraveWebSearchRequest request) {
        BraveWebSearchRequest safeRequest = request == null
                ? new BraveWebSearchRequest(null, null, null, null, null, null, null, null, null)
                : request;

        int count = clamp(
                safeRequest.count() == null ? properties.defaultCount() : safeRequest.count(),
                1,
                MAX_COUNT
        );
        int offset = clamp(safeRequest.offset() == null ? 0 : safeRequest.offset(), 0, MAX_OFFSET);

        return new BraveWebSearchRequest(
                trimToNull(safeRequest.query()),
                defaultIfBlank(safeRequest.country(), properties.country()),
                defaultIfBlank(safeRequest.searchLang(), properties.searchLang()),
                defaultIfBlank(safeRequest.uiLang(), properties.uiLang()),
                count,
                offset,
                trimToNull(safeRequest.freshness()),
                defaultIfBlank(safeRequest.safesearch(), properties.safesearch()),
                safeRequest.extraSnippets() == null ? properties.extraSnippets() : safeRequest.extraSnippets()
        );
    }

    private BraveSearchOutput toOutput(BraveSearchResponse response, String fallbackQuery) {
        BraveSearchResponse.Query query = response != null ? response.query() : null;
        List<BraveSearchResponse.Result> results = response == null || response.web() == null
                || response.web().results() == null
                ? List.of()
                : response.web().results();

        return new BraveSearchOutput(
                query != null && StringUtils.hasText(query.original()) ? query.original() : fallbackQuery,
                query != null ? query.altered() : null,
                query != null ? query.more_results_available() : null,
                results.stream()
                        .map(result -> new BraveSearchOutput.Result(
                                result.title(),
                                result.url(),
                                result.description(),
                                result.page_age(),
                                result.extra_snippets()
                        ))
                        .toList()
        );
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Brave Search output", e);
        }
    }

    private java.util.Optional<String> optionalText(String value) {
        return StringUtils.hasText(value) ? java.util.Optional.of(value) : java.util.Optional.empty();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
