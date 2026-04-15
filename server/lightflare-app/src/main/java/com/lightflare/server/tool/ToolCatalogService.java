package com.lightflare.server.tool;

import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tool.ToolParameterResponse;
import com.lightflare.server.tool.ToolResponse;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ToolCatalogService {

    private static final String MCP_TOOL_PREFIX = "mcp.";

    private final ToolService toolService;

    public List<ToolResponse> listTools() {
        return toolService.listTools().stream()
                .map(this::toResponse)
                .sorted(java.util.Comparator.comparing(ToolResponse::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ToolResponse::name))
                .toList();
    }

    private ToolResponse toResponse(ToolDefinition definition) {
        ToolSource source = resolveSource(definition.getName());
        return new ToolResponse(
                definition.getName(),
                definition.getDescription(),
                definition.getCategory(),
                definition.getIntegrationId(),
                source.type(),
                source.name(),
                toParameterResponses(definition.getProperties())
        );
    }

    private List<ToolParameterResponse> toParameterResponses(List<ToolInputDefinition> parameters) {
        if (CollectionUtils.isEmpty(parameters)) {
            return List.of();
        }
        return parameters.stream()
                .map(parameter -> new ToolParameterResponse(
                        parameter.getName(),
                        parameter.getType(),
                        parameter.isRequired(),
                        toParameterResponses(parameter.getProperties())
                ))
                .toList();
    }

    private ToolSource resolveSource(String toolName) {
        if (!StringUtils.hasText(toolName) || !toolName.startsWith(MCP_TOOL_PREFIX)) {
            return new ToolSource("LOCAL", "local");
        }

        String remainder = toolName.substring(MCP_TOOL_PREFIX.length());
        int separatorIndex = remainder.indexOf('.');
        if (separatorIndex <= 0) {
            return new ToolSource("MCP", remainder);
        }
        return new ToolSource("MCP", remainder.substring(0, separatorIndex));
    }

    private record ToolSource(String type, String name) {
    }
}
