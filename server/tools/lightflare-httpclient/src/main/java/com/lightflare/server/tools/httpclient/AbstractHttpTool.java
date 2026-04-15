package com.lightflare.server.tools.httpclient;

import com.lightflare.server.tools.core.*;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

abstract class AbstractHttpTool implements Tool {

    private final HttpClientService httpClientService;
    private final ToolDefinition definition;
    private final HttpMethod method;
    private final boolean supportsBody;

    protected AbstractHttpTool(
            HttpClientService httpClientService,
            ToolDefinition definition,
            HttpMethod method,
            boolean supportsBody
    ) {
        this.httpClientService = httpClientService;
        this.definition = definition;
        this.method = method;
        this.supportsBody = supportsBody;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolResult execute(List<ToolArgument> arguments, ToolExecutionContext context) {
        try {
            String url = HttpToolUtils.getStringArgument(arguments, "url");
            if (url == null) {
                return ToolResult.failure("url is required");
            }

            Map<String, String> headers = HttpToolUtils.getHeadersArgument(arguments);
            String body = supportsBody ? HttpToolUtils.getStringArgument(arguments, "body") : null;
            HttpClientService.HttpResponse response = httpClientService.execute(method, url, body, headers);
            return formatResponse(response);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage());
        } catch (Exception e) {
            return ToolResult.failure(method.name() + " request error: " + e.getMessage());
        }
    }

    private ToolResult formatResponse(HttpClientService.HttpResponse response) {
        String result = String.format("Status: %d%nResponse:%n%s", response.statusCode(), response.body());
        if (response.statusCode() >= 400) {
            return ToolResult.failure(result);
        }
        return ToolResult.success(result);
    }
}
