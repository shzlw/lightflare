package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import org.springframework.http.HttpMethod;

import java.util.List;

public class HttpPutTool extends AbstractHttpTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("http-put")
            .description("Execute an HTTP PUT request to a specified URL with optional body and headers.")
            .category("httpclient")
            .integrationId("httpclient")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("url")
                            .type("string")
                            .description("The full URL for the HTTP PUT request.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("body")
                            .type("string")
                            .description("The JSON or form-encoded request body.")
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
                    "Body can be a JSON string or form data. " +
                    "Example headers: {\"Authorization\": \"Bearer token\", \"Content-Type\": \"application/json\"}")
            .build();

    public HttpPutTool(HttpClientService httpClientService) {
        super(httpClientService, DEFINITION, HttpMethod.PUT, true);
    }
}
