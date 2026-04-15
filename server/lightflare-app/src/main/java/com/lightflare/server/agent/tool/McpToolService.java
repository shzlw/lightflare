package com.lightflare.server.agent.tool;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.mcpclient.McpClientRegistry;
import com.lightflare.server.mcpclient.NamedMcpClient;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private static final String MCP_TOOL_PREFIX = "mcp.";

    private final McpClientRegistry mcpClientRegistry;

    private volatile McpToolCatalog toolCatalog;

    public List<ToolDefinition> listTools() {
        return toolCatalog().definitions();
    }

    public Optional<ToolDefinition> findDefinition(String toolName) {
        if (!isMcpTool(toolName)) {
            return Optional.empty();
        }
        McpToolCatalog currentCatalog = toolCatalog();
        ToolDefinition definition = currentCatalog.definitionsByName().get(toolName);
        if (definition != null) {
            return Optional.of(definition);
        }
        currentCatalog = refreshToolCatalog();
        return Optional.ofNullable(currentCatalog.definitionsByName().get(toolName));
    }

    public boolean supports(String toolName) {
        return findDefinition(toolName).isPresent();
    }

    public ToolResult execute(String toolName, List<ToolArgument> arguments) {
        McpToolCatalog currentCatalog = toolCatalog();
        McpToolBinding binding = currentCatalog.bindingsByToolName().get(toolName);
        if (binding == null) {
            currentCatalog = refreshToolCatalog();
            binding = currentCatalog.bindingsByToolName().get(toolName);
        }
        if (binding == null) {
            throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
        }

        try {
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                binding.mcpToolName(),
                toMcpArguments(arguments, binding)
            );
            McpSchema.CallToolResult result = binding.client().callTool(request);
            return toToolResult(binding.qualifiedToolName(), result);
        } catch (RuntimeException e) {
            log.warn("MCP tool execution failed for {}", toolName, e);
            return ToolResult.failure("MCP tool execution failed for " + toolName + ": " + e.getMessage());
        }
    }

    private McpToolCatalog toolCatalog() {
        McpToolCatalog current = toolCatalog;
        if (current != null) {
            return current;
        }
        return refreshToolCatalog();
    }

    private synchronized McpToolCatalog refreshToolCatalog() {
        McpToolCatalog discovered = discoverTools();
        this.toolCatalog = discovered;
        return discovered;
    }

    private McpToolCatalog discoverTools() {
        Map<String, ToolDefinition> definitionsByName = new LinkedHashMap<>();
        Map<String, McpToolBinding> bindingsByToolName = new LinkedHashMap<>();

        for (NamedMcpClient client : mcpClientRegistry.listClients()) {
            try {
                McpSchema.ListToolsResult result = client.listTools();
                if (result == null || CollectionUtils.isEmpty(result.tools())) {
                    log.info("MCP server {} returned no tools", client.name());
                    continue;
                }

                for (McpSchema.Tool tool : result.tools()) {
                    if (tool == null || !StringUtils.hasText(tool.name())) {
                        continue;
                    }

                    String qualifiedToolName = qualifiedToolName(client.name(), tool.name());
                    ToolDefinition definition = ToolDefinition.builder()
                        .name(qualifiedToolName)
                        .description(toolDescription(client.name(), tool))
                        .category(toolCategory(client.name()))
                        .integrationId("mcp." + client.name())
                        .properties(toToolParameters(tool.inputSchema()))
                        .build();
                    definitionsByName.put(qualifiedToolName, definition);
                    bindingsByToolName.put(qualifiedToolName, new McpToolBinding(
                        qualifiedToolName,
                        tool.name(),
                        client,
                        propertyTypesByName(tool.inputSchema())
                    ));
                }
            } catch (RuntimeException e) {
                log.warn("Failed to discover tools from MCP server {}", client.name(), e);
            }
        }

        List<ToolDefinition> definitions = List.copyOf(definitionsByName.values());
        log.info("Discovered {} MCP tools from {} MCP servers",
            definitions.size(), mcpClientRegistry.listClients().size());
        return new McpToolCatalog(definitions, Map.copyOf(definitionsByName), Map.copyOf(bindingsByToolName));
    }

    private String qualifiedToolName(String serverName, String mcpToolName) {
        return MCP_TOOL_PREFIX + serverName + "." + mcpToolName;
    }

    private boolean isMcpTool(String toolName) {
        return StringUtils.hasText(toolName) && toolName.startsWith(MCP_TOOL_PREFIX);
    }

    private String toolCategory(String serverName) {
        if (!StringUtils.hasText(serverName)) {
            return "mcp";
        }
        return serverName.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private String toolDescription(String serverName, McpSchema.Tool tool) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(tool.description())) {
            parts.add(tool.description());
        } else if (StringUtils.hasText(tool.title())) {
            parts.add(tool.title());
        }
        parts.add("Source MCP server: " + serverName);
        return String.join(" ", parts);
    }

    private List<ToolInputDefinition> toToolParameters(McpSchema.JsonSchema schema) {
        if (schema == null || CollectionUtils.isEmpty(schema.properties())) {
            return List.of();
        }

        Set<String> required = schema.required() == null ? Set.of() : Set.copyOf(schema.required());
        return schema.properties().entrySet().stream()
            .filter(entry -> StringUtils.hasText(entry.getKey()))
            .map(entry -> toToolParameter(entry.getKey(), entry.getValue(), required))
            .filter(Objects::nonNull)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private ToolInputDefinition toToolParameter(String name, Object propertySchema, Set<String> requiredNames) {
        if (!(propertySchema instanceof Map<?, ?> propertyMap)) {
            return ToolInputDefinition.builder()
                .name(name)
                .type("string")
                .required(requiredNames.contains(name))
                .build();
        }

        String type = stringValue(propertyMap.get("type"));
        if (!StringUtils.hasText(type)) {
            type = inferTypeFromSchema(propertyMap);
        }

        List<ToolInputDefinition> nestedParameters = List.of();
        Object nestedProperties = propertyMap.get("properties");
        Object nestedRequired = propertyMap.get("required");
        if (nestedProperties instanceof Map<?, ?> nestedPropertiesMap) {
            Set<String> nestedRequiredNames = nestedRequired instanceof Collection<?> collection
                ? collection.stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toSet())
                : Set.of();
            nestedParameters = nestedPropertiesMap.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .map(entry -> toToolParameter((String) entry.getKey(), entry.getValue(), nestedRequiredNames))
                .filter(Objects::nonNull)
                .toList();
        }

        return ToolInputDefinition.builder()
            .name(name)
            .type(StringUtils.hasText(type) ? type : "string")
            .required(requiredNames.contains(name))
            .properties(nestedParameters)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> propertyTypesByName(McpSchema.JsonSchema schema) {
        if (schema == null || CollectionUtils.isEmpty(schema.properties())) {
            return Map.of();
        }

        Map<String, String> propertyTypes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : schema.properties().entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }

            String type = "string";
            if (entry.getValue() instanceof Map<?, ?> propertyMap) {
                type = stringValue(propertyMap.get("type"));
                if (!StringUtils.hasText(type)) {
                    type = inferTypeFromSchema((Map<?, ?>) entry.getValue());
                }
            }
            propertyTypes.put(entry.getKey(), StringUtils.hasText(type) ? type : "string");
        }
        return Map.copyOf(propertyTypes);
    }

    private String inferTypeFromSchema(Map<?, ?> propertyMap) {
        if (propertyMap.get("properties") instanceof Map<?, ?>) {
            return "object";
        }
        if (propertyMap.get("items") != null) {
            return "array";
        }
        return "string";
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private Map<String, Object> toMcpArguments(List<ToolArgument> arguments, McpToolBinding binding) {
        if (CollectionUtils.isEmpty(arguments)) {
            return Map.of();
        }

        Map<String, Object> mappedArguments = new LinkedHashMap<>();
        for (ToolArgument argument : arguments) {
            if (argument == null || !StringUtils.hasText(argument.getName())) {
                continue;
            }

            Object value = toArgumentValue(argument, binding.propertyTypes().get(argument.getName()));
            if (value != null) {
                mappedArguments.put(argument.getName(), value);
            }
        }
        return Map.copyOf(mappedArguments);
    }

    private Object toArgumentValue(ToolArgument argument, String type) {
        Object value = argument.getValue();
        if (value == null) {
            return null;
        }

        String effectiveType = StringUtils.hasText(type) ? type : "string";
        return switch (effectiveType) {
            case "integer" -> coerceInteger(value);
            case "number" -> coerceNumber(value);
            case "boolean" -> coerceBoolean(value);
            case "array" -> coerceArray(value);
            case "object" -> coerceObject(value);
            default -> coerceString(value);
        };
    }

    private Object coerceInteger(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return parseInteger(stringValue);
        }
        return value;
    }

    private Object parseInteger(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object coerceNumber(Object value) {
        if (value instanceof Number numberValue) {
            return numberValue;
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return parseNumber(stringValue);
        }
        return value;
    }

    private Object parseNumber(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object coerceBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            return Boolean.parseBoolean(stringValue);
        }
        return value;
    }

    private Object coerceArray(Object value) {
        if (value instanceof List<?>) {
            return value;
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            if (looksLikeJson(stringValue)) {
                Object parsed = JsonUtils.fromJson(stringValue);
                if (parsed instanceof List<?>) {
                    return parsed;
                }
            }
            return List.of(stringValue);
        }
        return List.of(value);
    }

    private Object coerceObject(Object value) {
        if (value instanceof Map<?, ?>) {
            return value;
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            if (looksLikeJson(stringValue)) {
                Object parsed = JsonUtils.fromJson(stringValue);
                if (parsed instanceof Map<?, ?>) {
                    return parsed;
                }
            }
            return stringValue;
        }
        return value;
    }

    private String coerceString(Object value) {
        return value instanceof String stringValue ? stringValue : String.valueOf(value);
    }

    private boolean looksLikeJson(String value) {
        return StringUtils.hasText(value)
            && ((value.startsWith("{") && value.endsWith("}"))
            || (value.startsWith("[") && value.endsWith("]")));
    }

    private ToolResult toToolResult(String toolName, McpSchema.CallToolResult result) {
        boolean success = result == null || !Boolean.TRUE.equals(result.isError());
        String content = formatToolResultContent(result);
        if (!StringUtils.hasText(content)) {
            content = success
                ? "MCP tool " + toolName + " completed successfully."
                : "MCP tool " + toolName + " failed without an error message.";
        }
        return success ? ToolResult.success(content) : ToolResult.failure(content);
    }

    private String formatToolResultContent(McpSchema.CallToolResult result) {
        if (result == null) {
            return null;
        }

        List<String> renderedParts = new ArrayList<>();
        if (!CollectionUtils.isEmpty(result.content())) {
            for (McpSchema.Content content : result.content()) {
                String rendered = renderContent(content);
                if (StringUtils.hasText(rendered)) {
                    renderedParts.add(rendered);
                }
            }
        }

        if (result.structuredContent() != null) {
            renderedParts.add(JsonUtils.toJson(result.structuredContent()));
        }

        return renderedParts.isEmpty() ? null : String.join("\n", renderedParts);
    }

    private String renderContent(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        if (content instanceof McpSchema.ImageContent imageContent) {
            return "Image content returned (mimeType=" + imageContent.mimeType() + ")";
        }
        if (content instanceof McpSchema.EmbeddedResource embeddedResource) {
            return renderResource(embeddedResource.resource());
        }
        return JsonUtils.toJson(content);
    }

    private String renderResource(McpSchema.ResourceContents resource) {
        if (resource instanceof McpSchema.TextResourceContents textResourceContents) {
            return textResourceContents.text();
        }
        if (resource instanceof McpSchema.BlobResourceContents blobResourceContents) {
            return "Binary resource returned from " + blobResourceContents.uri()
                + " (mimeType=" + blobResourceContents.mimeType() + ")";
        }
        return JsonUtils.toJson(resource);
    }

    private record McpToolCatalog(
        List<ToolDefinition> definitions,
        Map<String, ToolDefinition> definitionsByName,
        Map<String, McpToolBinding> bindingsByToolName
    ) {
    }

    private record McpToolBinding(
        String qualifiedToolName,
        String mcpToolName,
        NamedMcpClient client,
        Map<String, String> propertyTypes
    ) {
    }
}
