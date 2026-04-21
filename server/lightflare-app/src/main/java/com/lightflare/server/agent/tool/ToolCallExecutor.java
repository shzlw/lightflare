package com.lightflare.server.agent.tool;

import com.lightflare.server.harness.core.execution.PlanStepFormatter;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.llmproviders.core.LLMStepResponse;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolDefinition;
import com.lightflare.server.tools.core.ToolInputDefinition;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolCallExecutor {

    private static final String LOG_STAGE = "TOOL_EXEC";

    private final PlanStepFormatter planStepFormatter;

    public ToolResult handleToolAction(HarnessRunContext runContext,
                                       LLMPlanResponse.PlanStep step,
                                       LLMStepResponse stepResponse,
                                       List<String> runEntries,
                                       Set<String> executedToolCallSignatures) {
        String toolCallSignature = createToolCallSignature(stepResponse.getToolCall());
        if (!executedToolCallSignatures.add(toolCallSignature)) {
            log.info("[{}][DEDUPE_BLOCK] sessionId={}, stepId={}, signature={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    step.getId(),
                    toolCallSignature);
            String duplicateMessage = planStepFormatter.formatStepEntry(
                    step,
                    "TOOL_BLOCKED",
                    "Duplicate tool call blocked to avoid repeated side effects: " + toolCallSignature
            );
            runEntries.add(duplicateMessage);
            return ToolResult.failure(duplicateMessage);
        }

        log.info("[{}][CALL_START] sessionId={}, stepId={}, toolName={}, parameterCount={}",
                LOG_STAGE,
                runContext.executionId(),
                step.getId(),
                stepResponse.getToolCall() != null ? stepResponse.getToolCall().getToolName() : null,
                stepResponse.getToolCall() != null && stepResponse.getToolCall().getArguments() != null
                        ? stepResponse.getToolCall().getArguments().size()
                        : 0);
        ToolResult toolResult = executeToolCall(runContext, stepResponse.getToolCall());
        log.info("[{}][CALL_RESULT] sessionId={}, stepId={}, toolName={}, success={}",
                LOG_STAGE,
                runContext.executionId(),
                step.getId(),
                stepResponse.getToolCall() != null ? stepResponse.getToolCall().getToolName() : null,
                toolResult.success());
        String toolResultMessage = planStepFormatter.formatStepEntry(
                step,
                toolResult.success() ? "TOOL_SUCCESS" : "TOOL_FAILURE",
                toolResult.content()
        );
        runEntries.add(toolResultMessage);
        return toolResult;
    }

    public LLMStepResponse normalizeStepResponse(HarnessRunContext runContext, LLMStepResponse stepResponse) {
        if (stepResponse == null || stepResponse.getAction() == null) {
            return stepResponse;
        }

        LLMGetResponse.ToolCall toolCall = stepResponse.getToolCall();
        if (stepResponse.getAction() != LLMStepResponse.Action.REQUEST_TOOL_INPUT) {
            return stepResponse;
        }

        if (toolCall == null || !StringUtils.hasText(toolCall.getToolName())) {
            return stepResponse;
        }

        ToolDefinition toolDefinition;
        try {
            toolDefinition = findDefinition(runContext, toolCall.getToolName());
        } catch (IllegalArgumentException e) {
            return stepResponse;
        }
        if (CollectionUtils.isEmpty(toolDefinition.getProperties())) {
            return stepResponse;
        }

        Map<String, List<String>> valuesByName = toolCallParametersByName(toolCall);
        List<String> missingRequiredInputs = toolDefinition.getProperties().stream()
                .filter(Objects::nonNull)
                .filter(ToolInputDefinition::isRequired)
                .map(ToolInputDefinition::getName)
                .filter(StringUtils::hasText)
                .filter(name -> isMissing(valuesByName.get(name)))
                .toList();

        if (!missingRequiredInputs.isEmpty()) {
            stepResponse.setMissingInputs(missingRequiredInputs);
            return stepResponse;
        }

        log.info("[{}][NORMALIZE_RESPONSE] toolName={}, fromAction=REQUEST_TOOL_INPUT, toAction=USE_TOOL",
                LOG_STAGE, toolCall.getToolName());
        stepResponse.setAction(LLMStepResponse.Action.USE_TOOL);
        stepResponse.setMissingInputs(List.of());
        if (!StringUtils.hasText(stepResponse.getResponse())) {
            stepResponse.setResponse(null);
        }
        return stepResponse;
    }

    public String buildMissingToolInputResponse(LLMStepResponse stepResponse) {
        if (StringUtils.hasText(stepResponse.getResponse())) {
            return stepResponse.getResponse();
        }
        if (CollectionUtils.isEmpty(stepResponse.getMissingInputs())) {
            return "More tool input is required.";
        }
        return "Missing required tool inputs: " + String.join(", ", stepResponse.getMissingInputs());
    }

    private ToolResult executeToolCall(HarnessRunContext runContext, LLMGetResponse.ToolCall toolCall) {
        if (toolCall == null || !StringUtils.hasText(toolCall.getToolName())) {
            throw new IllegalArgumentException("Tool action requires a toolCall with toolName");
        }

        List<ToolArgument> arguments = CollectionUtils.isEmpty(toolCall.getArguments())
                ? List.of()
                : toolCall.getArguments().stream()
                .filter(argument -> argument != null && StringUtils.hasText(argument.getName()))
                .map(argument -> ToolArgument.builder()
                        .name(argument.getName())
                        .value(toToolArgumentValue(argument))
                        .build())
                .toList();

        try {
            log.info("[{}][TOOL_SERVICE_CALL] toolName={}, argumentCount={}",
                    LOG_STAGE, toolCall.getToolName(), arguments.size());
            if (runContext == null || runContext.toolExecutionRouter() == null) {
                return ToolResult.failure("Tool execution router is not configured.");
            }
            return runContext.toolExecutionRouter().execute(toolCall.getToolName(), arguments, runContext.userId());
        } catch (RuntimeException e) {
            log.warn("[{}][TOOL_SERVICE_FAIL] toolName={}", LOG_STAGE, toolCall.getToolName(), e);
            return ToolResult.failure(
                    "Tool execution failed for " + toolCall.getToolName()
                            + ": " + e.getMessage()
            );
        }
    }

    private ToolDefinition findDefinition(HarnessRunContext runContext, String toolName) {
        if (runContext != null && runContext.toolExecutionRouter() != null) {
            return runContext.toolExecutionRouter().findDefinition(toolName);
        }
        throw new IllegalArgumentException("Tool execution router is not configured.");
    }

    private Map<String, List<String>> toolCallParametersByName(LLMGetResponse.ToolCall toolCall) {
        if (toolCall == null || CollectionUtils.isEmpty(toolCall.getArguments())) {
            return Map.of();
        }

        return toolCall.getArguments().stream()
                .filter(Objects::nonNull)
                .filter(argument -> StringUtils.hasText(argument.getName()))
                .collect(Collectors.toMap(
                        LLMGetResponse.ToolCallArgument::getName,
                        argument -> copyValues(argument.getValues()),
                        (left, right) -> !isMissing(right) ? right : left
                ));
    }

    private List<String> copyValues(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private Object toToolArgumentValue(LLMGetResponse.ToolCallArgument argument) {
        List<String> values = copyValues(argument.getValues());
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        return values;
    }

    private boolean isMissing(Collection<String> values) {
        return values == null || values.stream().noneMatch(StringUtils::hasText);
    }

    private String createToolCallSignature(LLMGetResponse.ToolCall toolCall) {
        if (toolCall == null) {
            return "null-tool-call";
        }

        Map<String, Object> normalizedParameters = new TreeMap<>();
        if (!CollectionUtils.isEmpty(toolCall.getArguments())) {
            for (LLMGetResponse.ToolCallArgument argument : toolCall.getArguments()) {
                if (argument == null || !StringUtils.hasText(argument.getName())) {
                    continue;
                }
                List<String> values = argument.getValues();
                normalizedParameters.put(
                        argument.getName(),
                        CollectionUtils.isEmpty(values) ? List.of() : values
                );
            }
        }

        return toolCall.getToolName() + ":" + JsonUtils.toJson(normalizedParameters);
    }
}
