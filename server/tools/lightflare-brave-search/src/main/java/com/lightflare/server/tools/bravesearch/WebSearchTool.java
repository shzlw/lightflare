package com.lightflare.server.tools.bravesearch;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class WebSearchTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("web-search")
            .description("Search the web and return current web results with titles, URLs, snippets, page age, and optional extra snippets.")
            .category("web")
            .integrationId("brave_search")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("query")
                            .type("string")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("count")
                            .type("integer")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("offset")
                            .type("integer")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("freshness")
                            .type("string")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("country")
                            .type("string")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("search_lang")
                            .type("string")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("ui_lang")
                            .type("string")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("safesearch")
                            .type("string")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("extra_snippets")
                            .type("boolean")
                            .required(false)
                            .build()
            ))
            .build();

    private final BraveSearchService braveSearchService;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String query = readStringArgument(arguments, "query");
        if (query == null) {
            return ToolResult.failure("query is required");
        }

        try {
            return ToolResult.success(braveSearchService.search(new BraveWebSearchRequest(
                    query,
                    readStringArgument(arguments, "country"),
                    readStringArgument(arguments, "search_lang"),
                    readStringArgument(arguments, "ui_lang"),
                    readIntegerArgument(arguments, "count"),
                    readIntegerArgument(arguments, "offset"),
                    readStringArgument(arguments, "freshness"),
                    readStringArgument(arguments, "safesearch"),
                    readBooleanArgument(arguments, "extra_snippets")
            )));
        } catch (RuntimeException e) {
            return ToolResult.failure(e.getMessage());
        }
    }

    private String readStringArgument(List<ToolArgument> arguments, String name) {
        return arguments == null ? null : arguments.stream()
                .filter(argument -> name.equals(argument.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private Integer readIntegerArgument(List<ToolArgument> arguments, String name) {
        try {
            return arguments == null ? null : arguments.stream()
                    .filter(argument -> name.equals(argument.getName()))
                    .map(ToolArgument::asInteger)
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }

    private Boolean readBooleanArgument(List<ToolArgument> arguments, String name) {
        try {
            return arguments == null ? null : arguments.stream()
                    .filter(argument -> name.equals(argument.getName()))
                    .map(ToolArgument::asBoolean)
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " must be a boolean", e);
        }
    }
}
