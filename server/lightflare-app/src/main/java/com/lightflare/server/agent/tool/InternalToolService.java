package com.lightflare.server.agent.tool;

import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolExecutionContext;
import com.lightflare.server.tools.core.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class InternalToolService {

    private final Map<String, InternalTool> toolsByName;

    public InternalToolService(List<InternalTool> tools) {
        this.toolsByName = new LinkedHashMap<>();
        for (InternalTool tool : tools == null ? List.<InternalTool>of() : tools) {
            register(tool);
        }
    }

    public List<ToolDefinition> listTools() {
        return toolsByName.values().stream()
                .map(InternalTool::definition)
                .toList();
    }

    public boolean supports(String toolName) {
        return toolsByName.containsKey(toolName);
    }

    public ToolDefinition findDefinition(String toolName) {
        InternalTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown internal tool: " + toolName);
        }
        return tool.definition();
    }

    public ToolResult execute(String toolName, List<ToolArgument> arguments, String userId) {
        InternalTool tool = toolsByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown internal tool: " + toolName);
        }
        log.info("Executing internal toolName={}, argumentCount={}",
                toolName, arguments != null ? arguments.size() : 0);
        return tool.execute(arguments != null ? arguments : List.of(),
                new ToolExecutionContext(tool.definition(), userId));
    }

    private void register(InternalTool tool) {
        if (tool == null) {
            throw new IllegalStateException("Internal tool registry received a null tool bean");
        }
        ToolDefinition definition = tool.definition();
        if (definition == null) {
            throw new IllegalStateException("Internal tool " + tool.getClass().getName()
                    + " returned a null definition");
        }
        if (!StringUtils.hasText(definition.getName())) {
            throw new IllegalStateException("Internal tool " + tool.getClass().getName()
                    + " has an empty tool definition name");
        }
        InternalTool existing = toolsByName.putIfAbsent(definition.getName(), tool);
        if (existing != null) {
            throw new IllegalStateException("Duplicate internal tool definition name '"
                    + definition.getName() + "' found for " + existing.getClass().getName()
                    + " and " + tool.getClass().getName());
        }
    }
}
