package com.lightflare.server.integration;

import java.util.List;
import java.util.Map;

public record McpResponse(
        String name,
        String transport,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        String endpoint,
        String sseEndpoint
) {
}
