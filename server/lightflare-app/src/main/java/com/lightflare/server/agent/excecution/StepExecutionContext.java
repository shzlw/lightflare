package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.prompts.MemoryPromptItem;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.tools.core.ToolDefinition;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

public record StepExecutionContext(
        String sessionId,
        String userId,
        String task,
        List<MemoryPromptItem> promptMemories,
        List<ToolDefinition> tools,
        String selectedSkillName,
        String selectedSkillInstructions,
        List<String> executionLog
) {

    public StepExecutionContext {
        promptMemories = promptMemories == null ? List.of() : List.copyOf(promptMemories);
        tools = tools == null ? List.of() : List.copyOf(tools);
        executionLog = executionLog == null ? List.of() : List.copyOf(executionLog);
    }

    public List<String> dependencyContextFor(LLMPlanResponse.PlanStep step) {
        if (step == null || CollectionUtils.isEmpty(step.getDependsOn()) || executionLog.isEmpty()) {
            return List.of();
        }

        return executionLog.stream()
                .filter(StringUtils::hasText)
                .filter(entry -> dependsOnEntry(step.getDependsOn(), entry))
                .filter(entry -> entry.contains("[STEP_RESULT]") || entry.contains("[PARTIAL_RESPONSE]"))
                .toList();
    }

    public List<ToolDefinition> toolsFor(LLMPlanResponse.PlanStep step) {
        if (step == null || !StringUtils.hasText(step.getToolCategory())) {
            return tools;
        }

        List<ToolDefinition> matchingTools = tools.stream()
                .filter(tool -> tool != null && step.getToolCategory().equalsIgnoreCase(tool.getCategory()))
                .toList();
        return matchingTools.isEmpty() ? tools : matchingTools;
    }

    private boolean dependsOnEntry(List<String> dependencyIds, String entry) {
        for (String dependencyId : dependencyIds) {
            if (StringUtils.hasText(dependencyId) && entry.startsWith("[" + dependencyId + "]")) {
                return true;
            }
        }
        return false;
    }
}
