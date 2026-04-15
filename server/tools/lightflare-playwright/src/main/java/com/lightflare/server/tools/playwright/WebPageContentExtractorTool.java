package com.lightflare.server.tools.playwright;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class WebPageContentExtractorTool implements Tool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("web-page-content-extractor")
            .description("Fetch a web page and extract its main textual content.")
            .category("web")
            .integrationId("playwright")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("url")
                            .type("string")
                            .required(true)
                            .build()
            ))
            .build();

    private final WebPageContentExtractor webPageContentExtractor;

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        String url = arguments.stream()
                .filter(parameter -> "url".equals(parameter.getName()))
                .map(ToolArgument::asString)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (url == null) {
            return ToolResult.failure("Missing required argument: url");
        }

        String content = webPageContentExtractor.fetchPageContent(url);
        return ToolResult.success(content);
    }
}
