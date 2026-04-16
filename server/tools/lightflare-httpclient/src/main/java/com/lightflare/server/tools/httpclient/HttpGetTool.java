package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import org.springframework.http.HttpMethod;

import java.util.List;

public class HttpGetTool extends AbstractHttpTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("http-get")
            .description("Execute an HTTP GET request to a specified URL with optional headers.")
            .category("httpclient")
            .integrationId("httpclient")
            .properties(List.of(
                    ToolInputDefinition.builder()
                            .name("url")
                            .type("string")
                            .description("The full URL for the HTTP GET request.")
                            .required(true)
                            .build(),
                    ToolInputDefinition.builder()
                            .name("headers")
                            .type("object")
                            .description("An object representing HTTP headers (e.g., {'Authorization': 'Bearer token'}).")
                            .required(false)
                            .build()
            ))
            .usageGuidance("Provide a URL and optionally headers as an object. " +
                    "Example headers: {\"Authorization\": \"Bearer token\", \"Custom-Header\": \"value\"}")
            .build();

    public HttpGetTool(HttpClientService httpClientService) {
        super(httpClientService, DEFINITION, HttpMethod.GET, false);
    }
}
