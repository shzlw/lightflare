package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import org.springframework.http.HttpMethod;

import java.util.List;

public class HttpDeleteTool extends AbstractHttpTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("http-delete")
            .description("Execute an HTTP DELETE request to a specified URL with optional body and headers.")
            .category("httpclient")
            .integrationId("httpclient")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("url")
                            .type("string")
                            .description("The full URL for the HTTP DELETE request.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("body")
                            .type("string")
                            .description("Optional request body for the DELETE request.")
                            .required(false)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("headers")
                            .type("object")
                            .description("An object representing HTTP headers (e.g., {'Content-Type': 'application/json'}).")
                            .required(false)
                            .build()
            ))
            .usageGuidance("Provide a URL and optionally a request body and headers object. " +
                    "Body can include data for the DELETE request if needed. " +
                    "Example headers: {\"Authorization\": \"Bearer token\", \"Content-Type\": \"application/json\"}")
            .build();

    public HttpDeleteTool(HttpClientService httpClientService) {
        super(httpClientService, DEFINITION, HttpMethod.DELETE, true);
    }
}
