package com.lightflare.server.tool;

import java.util.List;

public record ToolParameterResponse(
        String name,
        String type,
        boolean required,
        List<ToolParameterResponse> parameters
) {
}
