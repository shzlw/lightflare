package com.lightflare.server.tool;

import java.util.List;

public record ToolResponse(
        String name,
        String description,
        String category,
        String integrationId,
        String sourceType,
        String sourceName,
        List<ToolParameterResponse> parameters
) {
}
