package com.lightflare.server.agent.tool;

import com.lightflare.server.tools.core.Tool;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolSelection;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName;
    private final Map<String, ToolSelection> toolSelectionsByIntegrationId;
    private final Map<String, Set<String>> toolNamesByIntegrationId;

    public ToolRegistry(List<Tool> tools, List<ToolSelection> toolSelections) {
        this.toolsByName = new LinkedHashMap<>();
        this.toolSelectionsByIntegrationId = indexToolSelections(toolSelections);
        this.toolNamesByIntegrationId = new HashMap<>();
        for (Tool tool : tools) {
            register(tool);
        }
        validateEnabledToolNames();
    }

    private void register(Tool tool) {
        if (tool == null) {
            throw new IllegalStateException("Tool registry received a null tool bean");
        }

        ToolDefinition definition = tool.definition();
        if (definition == null) {
            throw new IllegalStateException("Tool " + tool.getClass().getName() + " returned a null definition");
        }

        String toolDefinitionName = definition.getName();
        if (!StringUtils.hasText(toolDefinitionName)) {
            throw new IllegalStateException("Tool " + tool.getClass().getName()
                    + " has an empty tool definition name");
        }
        if (!StringUtils.hasText(definition.getCategory())) {
            throw new IllegalStateException("Tool " + tool.getClass().getName()
                    + " has an empty tool definition category");
        }
        if (!StringUtils.hasText(definition.getIntegrationId())) {
            throw new IllegalStateException("Tool " + tool.getClass().getName()
                    + " has an empty tool definition integrationId");
        }

        String integrationId = normalizeName(definition.getIntegrationId());
        String toolName = normalizeName(toolDefinitionName);
        toolNamesByIntegrationId.computeIfAbsent(integrationId, ignored -> new HashSet<>()).add(toolName);
        if (!isToolEnabled(integrationId, toolName)) {
            return;
        }

        Tool existing = toolsByName.putIfAbsent(toolDefinitionName, tool);
        if (existing != null) {
            throw new IllegalStateException("Duplicate tool definition name '" + toolDefinitionName
                    + "' found for " + existing.getClass().getName()
                    + " and " + tool.getClass().getName()
                    + ". Tool definition names must be unique.");
        }
    }

    private Map<String, ToolSelection> indexToolSelections(List<ToolSelection> toolSelections) {
        Map<String, ToolSelection> indexedSelections = new HashMap<>();
        for (ToolSelection selection : toolSelections == null ? List.<ToolSelection>of() : toolSelections) {
            if (selection == null || !StringUtils.hasText(selection.integrationId())) {
                continue;
            }
            ToolSelection existing = indexedSelections.putIfAbsent(normalizeName(selection.integrationId()), selection);
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool selection config for integration " + selection.integrationId());
            }
        }
        return indexedSelections;
    }

    private boolean isToolEnabled(String integrationId, String toolName) {
        ToolSelection selection = toolSelectionsByIntegrationId.get(integrationId);
        if (selection == null) {
            return true;
        }
        return enabledToolNames(selection).contains(toolName);
    }

    private void validateEnabledToolNames() {
        List<String> unknownToolNames = new ArrayList<>();
        for (Map.Entry<String, ToolSelection> entry : toolSelectionsByIntegrationId.entrySet()) {
            if (!entry.getValue().enabled()) {
                continue;
            }
            String integrationId = entry.getKey();
            Set<String> availableToolNames = toolNamesByIntegrationId.getOrDefault(integrationId, Set.of());
            for (String enabledToolName : enabledToolNames(entry.getValue())) {
                if (!availableToolNames.contains(enabledToolName)) {
                    unknownToolNames.add(integrationId + "." + enabledToolName);
                }
            }
        }
        if (!unknownToolNames.isEmpty()) {
            throw new IllegalStateException("Unknown enabled tool name(s): " + String.join(", ", unknownToolNames));
        }
    }

    private Set<String> enabledToolNames(ToolSelection selection) {
        return selection.enabledTools() == null ? Set.of() : selection.enabledTools().stream()
                .filter(StringUtils::hasText)
                .map(ToolRegistry::normalizeName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase().replace('_', '-');
    }

    public Collection<Tool> list() {
        return List.copyOf(toolsByName.values());
    }

    public Optional<Tool> find(String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }
}
