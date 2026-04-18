package com.lightflare.server.workflow;

import com.lightflare.server.agent.AgentTaskService;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowService workflowService;
    private final WorkflowRunRepository runRepository;
    private final WorkflowStepRunRepository stepRunRepository;
    private final ToolService toolService;
    private final AgentTaskService agentTaskService;

    public String execute(String workflowId) {
        return execute(workflowId, Collections.emptyMap(), null);
    }

    public String execute(String workflowId, Map<String, Object> initialData) {
        return execute(workflowId, initialData, null);
    }

    public String execute(String workflowId, Map<String, Object> initialData, String startStepId) {
        return execute(workflowId, initialData, startStepId, null, "manual", null);
    }

    public String execute(String workflowId,
                          Map<String, Object> initialData,
                          String startStepId,
                          String userId,
                          String triggerType,
                          String sourceId) {
        return execute(workflowId, initialData, startStepId, userId, triggerType, sourceId, null);
    }

    public String execute(String workflowId,
                          Map<String, Object> initialData,
                          String startStepId,
                          String userId,
                          String triggerType,
                          String sourceId,
                          String triggerId) {
        Workflow workflow = workflowService.getWorkflow(workflowId);
        String runId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> input = initialData != null ? initialData : Collections.emptyMap();
        runRepository.insertRun(
                runId,
                workflow.getId(),
                triggerId,
                triggerType != null ? triggerType : "manual",
                "RUNNING",
                JsonUtils.toJson(input),
                null,
                null,
                userId,
                sourceId,
                now,
                null,
                now
        );

        try {
            Map<String, Object> output = runSteps(runId, workflow, input, startStepId, userId);
            runRepository.completeRun(runId, "COMPLETED", JsonUtils.toJson(output), null, OffsetDateTime.now());
        } catch (Exception e) {
            runRepository.completeRun(runId, "FAILED", null, e.getMessage(), OffsetDateTime.now());
        }
        return runId;
    }

    public Object testStep(WorkflowStepDefinition step, Map<String, Object> mockContext) {
        return executeStep(step, mockContext != null ? mockContext : Collections.emptyMap(), null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runSteps(String runId,
                                         Workflow workflow,
                                         Map<String, Object> input,
                                         String startStepId,
                                         String userId) {
        Object parsed = JsonUtils.fromJson(workflow.getSchemaDefinition());
        if (!(parsed instanceof Map<?, ?> definitionMap)) {
            throw new IllegalArgumentException("Workflow definition must be a JSON object.");
        }
        Object stepsValue = definitionMap.get("steps");
        if (!(stepsValue instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
            return Map.of("message", "Workflow has no steps.", "input", input);
        }

        List<WorkflowStepDefinition> steps = rawSteps.stream()
                .filter(Map.class::isInstance)
                .map(step -> stepDefinition((Map<String, Object>) step))
                .toList();
        List<WorkflowStepDefinition> executableSteps = filterStartStep(steps, startStepId);

        Map<String, Object> context = new HashMap<>();
        context.put("inputs", input);
        context.put("steps", new HashMap<String, Object>());
        Map<String, Object> finalOutput = new HashMap<>();

        for (WorkflowStepDefinition step : executableSteps) {
            String stepRunId = UUID.randomUUID().toString();
            OffsetDateTime startedAt = OffsetDateTime.now();
            Map<String, Object> stepInput = stepInput(step, context);
            stepRunRepository.insertStepRun(
                    stepRunId,
                    runId,
                    step.resolvedId(),
                    step.name(),
                    normalizeStepType(step.type()),
                    "RUNNING",
                    JsonUtils.toJson(stepInput),
                    null,
                    null,
                    startedAt,
                    null
            );

            try {
                Object output = executeStep(step, stepInput, userId, context);
                Map<String, Object> stepState = new HashMap<>();
                stepState.put("input", stepInput);
                stepState.put("output", output);
                ((Map<String, Object>) context.get("steps")).put(step.resolvedId(), stepState);
                finalOutput.put(step.resolvedId(), output);
                stepRunRepository.completeStepRun(stepRunId, "COMPLETED", JsonUtils.toJson(output), null, OffsetDateTime.now());
            } catch (Exception e) {
                Map<String, Object> stepState = new HashMap<>();
                stepState.put("input", stepInput);
                stepState.put("error", e.getMessage());
                ((Map<String, Object>) context.get("steps")).put(step.resolvedId(), stepState);
                stepRunRepository.completeStepRun(stepRunId, "FAILED", null, e.getMessage(), OffsetDateTime.now());
                if (!"continue".equalsIgnoreCase(step.onError())) {
                    throw new IllegalStateException("Workflow step failed: " + step.resolvedId() + ": " + e.getMessage(), e);
                }
            }
        }

        return finalOutput;
    }

    private List<WorkflowStepDefinition> filterStartStep(List<WorkflowStepDefinition> steps, String startStepId) {
        if (!StringUtils.hasText(startStepId)) {
            return steps;
        }
        int index = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (startStepId.equals(steps.get(i).resolvedId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Start step not found: " + startStepId);
        }
        return steps.subList(index, steps.size());
    }

    @SuppressWarnings("unchecked")
    private WorkflowStepDefinition stepDefinition(Map<String, Object> value) {
        return new WorkflowStepDefinition(
                stringValue(value.get("id")),
                stringValue(value.get("stepId")),
                stringValue(value.get("name")),
                stringValue(value.get("type")),
                firstStringValue(value.get("toolName"), value.get("actionIdentifier")),
                stringValue(value.get("prompt")),
                value.get("input") instanceof Map<?, ?> map ? (Map<String, Object>) map : null,
                value.get("output") instanceof Map<?, ?> map ? (Map<String, Object>) map : null,
                stringValue(value.get("onError"))
        );
    }

    private Map<String, Object> stepInput(WorkflowStepDefinition step, Map<String, Object> context) {
        Map<String, Object> input = new HashMap<>();
        if (step.input() != null) {
            step.input().forEach((key, value) -> input.put(key, resolveValue(value, context)));
        }
        if (step.prompt() != null) {
            input.put("prompt", resolveText(step.prompt(), context));
        }
        return input;
    }

    private Object executeStep(WorkflowStepDefinition step,
                               Map<String, Object> stepInput,
                               String userId,
                               Map<String, Object> context) {
        String type = normalizeStepType(step.type());
        return switch (type) {
            case "tool" -> executeToolStep(step, stepInput, userId);
            case "llm" -> executeLlmStep(step, stepInput, userId, context);
            case "condition" -> Map.of("passed", true, "input", stepInput);
            default -> throw new IllegalArgumentException("Unsupported workflow step type: " + type);
        };
    }

    private Object executeToolStep(WorkflowStepDefinition step, Map<String, Object> stepInput, String userId) {
        String toolName = step.toolName();
        if (!StringUtils.hasText(toolName)) {
            throw new IllegalArgumentException("Tool step is missing toolName.");
        }
        List<ToolArgument> arguments = new ArrayList<>();
        stepInput.forEach((name, value) -> arguments.add(new ToolArgument(name, value)));
        ToolResult result = toolService.execute(toolName, arguments, userId);
        if (result == null || !result.success()) {
            throw new IllegalStateException(result != null ? result.content() : "Tool returned no result.");
        }
        return parseToolResult(result.content());
    }

    private Object executeLlmStep(WorkflowStepDefinition step,
                                  Map<String, Object> stepInput,
                                  String userId,
                                  Map<String, Object> context) {
        String prompt = stepInput.get("prompt") instanceof String value ? value : step.prompt();
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("LLM step is missing prompt.");
        }
        String executionId = "workflow-" + UUID.randomUUID();
        String task = """
                Execute this workflow step and return the step result.

                Step id: %s
                Step name: %s

                Step instruction:
                %s

                Workflow context JSON:
                %s
                """.formatted(
                step.resolvedId(),
                StringUtils.hasText(step.name()) ? step.name() : step.resolvedId(),
                prompt,
                JsonUtils.toJson(context != null ? context : Map.of())
        );
        String response = agentTaskService.executeWorkflowStep(executionId, step.resolvedId(), userId, task);
        return Map.of("text", response != null ? response : "");
    }

    private Object parseToolResult(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of("text", "");
        }
        try {
            return JsonUtils.fromJson(content);
        } catch (Exception ignored) {
            return Map.of("text", content);
        }
    }

    private String normalizeStepType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase() : "llm";
    }

    private Object resolveValue(Object value, Map<String, Object> context) {
        if (value instanceof String stringValue) {
            return resolveText(stringValue, context);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> resolved = new HashMap<>();
            mapValue.forEach((key, nestedValue) -> resolved.put(String.valueOf(key), resolveValue(nestedValue, context)));
            return resolved;
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(item -> resolveValue(item, context)).toList();
        }
        return value;
    }

    private String resolveText(String value, Map<String, Object> context) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String resolved = value;
        Object inputs = context != null ? context.get("inputs") : null;
        if (inputs instanceof Map<?, ?> inputMap) {
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                resolved = resolved.replace("{{inputs." + entry.getKey() + "}}", String.valueOf(entry.getValue()));
            }
        }
        Object steps = context != null ? context.get("steps") : null;
        if (steps instanceof Map<?, ?> stepMap) {
            for (Map.Entry<?, ?> entry : stepMap.entrySet()) {
                String stepId = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> state) {
                    Object output = state.get("output");
                    resolved = resolved.replace("{{steps." + stepId + ".output}}", JsonUtils.toJson(output));
                    if (output instanceof Map<?, ?> outputMap) {
                        for (Map.Entry<?, ?> outputEntry : outputMap.entrySet()) {
                            resolved = resolved.replace(
                                    "{{steps." + stepId + ".output." + outputEntry.getKey() + "}}",
                                    String.valueOf(outputEntry.getValue())
                            );
                        }
                    }
                }
            }
        }
        return resolved;
    }

    private String firstStringValue(Object... values) {
        for (Object value : values) {
            String stringValue = stringValue(value);
            if (StringUtils.hasText(stringValue)) {
                return stringValue;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
