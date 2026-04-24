package com.lightflare.server.application;

import com.lightflare.server.agent.AgentTaskService;
import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ApplicationEngine {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Pattern EXACT_TEMPLATE_PATTERN = Pattern.compile("^\\s*\\{\\{\\s*([^{}]+?)\\s*}}\\s*$");
    private static final Object UNRESOLVED_TEMPLATE = new Object();

    private final ApplicationRepository applicationRepository;
    private final ApplicationVersionRepository applicationVersionRepository;
    private final ApplicationStepRepository applicationStepRepository;
    private final ApplicationEdgeRepository applicationEdgeRepository;
    private final ApplicationTriggerRepository applicationTriggerRepository;
    private final ApplicationRunRepository applicationRunRepository;
    private final ApplicationStepRunRepository applicationStepRunRepository;
    private final ToolService toolService;
    private final AgentTaskService agentTaskService;

    public String execute(String applicationId,
                          Map<String, Object> initialData,
                          String versionId,
                          String startStepId,
                          String userId,
                          String triggerId,
                          ApplicationExecutionListener listener) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        ApplicationVersion version = resolveVersion(application, versionId);
        ApplicationTrigger trigger = resolveTrigger(version.getId(), triggerId);
        ApplicationExecutionListener executionListener = listener != null ? listener : ApplicationExecutionListener.NOOP;
        String resolvedStartStepId = resolveStartStepId(startStepId, trigger);

        String runId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> input = mergeTriggerInput(initialData, trigger);
        applicationRunRepository.insertRun(
                runId,
                application.getId(),
                version.getId(),
                trigger != null ? trigger.getId() : null,
                ApplicationRun.STATUS_RUNNING,
                JsonUtils.toJson(input),
                null,
                null,
                userId,
                now,
                null
        );
        executionListener.runStarted(runId);

        try {
            Map<String, Object> output = runSteps(runId, version.getId(), input, resolvedStartStepId, userId, executionListener);
            applicationRunRepository.completeRun(runId, ApplicationRun.STATUS_COMPLETED, JsonUtils.toJson(output), null, OffsetDateTime.now());
            executionListener.runCompleted(runId, output);
        } catch (Exception exception) {
            applicationRunRepository.completeRun(runId, ApplicationRun.STATUS_FAILED, null, exception.getMessage(), OffsetDateTime.now());
            executionListener.runFailed(runId, exception.getMessage());
        }
        return runId;
    }

    private ApplicationVersion resolveVersion(Application application, String versionId) {
        ApplicationVersion version;
        if (StringUtils.hasText(versionId)) {
            version = applicationVersionRepository.findById(versionId)
                    .orElseThrow(() -> new IllegalArgumentException("Application version not found: " + versionId));
        } else if (StringUtils.hasText(application.getPublishedVersionId())) {
            version = applicationVersionRepository.findById(application.getPublishedVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Published application version not found: " + application.getPublishedVersionId()));
        } else {
            version = applicationVersionRepository.findLatestByApplicationId(application.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Application has no versions: " + application.getId()));
        }
        if (!application.getId().equals(version.getApplicationId())) {
            throw new IllegalArgumentException("Application version does not belong to application: " + application.getId());
        }
        if (ApplicationVersion.STATUS_ARCHIVED.equalsIgnoreCase(version.getStatus())) {
            throw new IllegalStateException("Archived application versions cannot be executed.");
        }
        return version;
    }

    private ApplicationTrigger resolveTrigger(String applicationVersionId, String triggerId) {
        if (!StringUtils.hasText(triggerId)) {
            return null;
        }
        ApplicationTrigger trigger = applicationTriggerRepository.findById(triggerId)
                .orElseThrow(() -> new IllegalArgumentException("Application trigger not found: " + triggerId));
        if (!applicationVersionId.equals(trigger.getApplicationVersionId())) {
            throw new IllegalArgumentException("Application trigger does not belong to application version: " + applicationVersionId);
        }
        return trigger;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeTriggerInput(Map<String, Object> initialData, ApplicationTrigger trigger) {
        Map<String, Object> merged = new HashMap<>();
        if (trigger != null && StringUtils.hasText(trigger.getConfigJson())) {
            Object parsedConfig = JsonUtils.fromJson(trigger.getConfigJson());
            if (parsedConfig instanceof Map<?, ?> config) {
                if (config.get("inputFields") instanceof List<?> inputFields) {
                    for (Object field : inputFields) {
                        if (!(field instanceof Map<?, ?> fieldMap)) {
                            continue;
                        }
                        String name = stringValue(fieldMap.get("name"));
                        if (StringUtils.hasText(name) && fieldMap.containsKey("default")) {
                            merged.put(name, fieldMap.get("default"));
                        }
                    }
                }
                if (config.get("input") instanceof Map<?, ?> input) {
                    input.forEach((key, value) -> merged.put(String.valueOf(key), value));
                }
            }
        }
        if (initialData != null) {
            merged.putAll(initialData);
        }
        return merged;
    }

    private String resolveStartStepId(String startStepId, ApplicationTrigger trigger) {
        if (StringUtils.hasText(startStepId)) {
            return startStepId.trim();
        }
        return trigger != null ? trigger.getStartStepId() : null;
    }

    private Map<String, Object> runSteps(String runId,
                                         String applicationVersionId,
                                         Map<String, Object> input,
                                         String startStepId,
                                         String userId,
                                         ApplicationExecutionListener listener) {
        List<ApplicationStep> stepRows = applicationStepRepository.findByApplicationVersionId(applicationVersionId);
        if (stepRows.isEmpty()) {
            return Map.of("message", "Application has no steps.", "input", input);
        }
        List<ApplicationStepDefinition> steps = stepRows.stream()
                .sorted(Comparator.comparing(ApplicationStep::getStepKey, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ApplicationStep::getId))
                .map(this::stepDefinition)
                .toList();
        Map<String, ApplicationStepDefinition> stepsById = new HashMap<>();
        for (ApplicationStepDefinition step : steps) {
            stepsById.put(step.resolvedId(), step);
        }

        String resolvedStartStepId = StringUtils.hasText(startStepId)
                ? startStepId
                : steps.get(0).resolvedId();
        if (!stepsById.containsKey(resolvedStartStepId)) {
            throw new IllegalArgumentException("Start step not found: " + resolvedStartStepId);
        }

        Map<String, List<ApplicationEdge>> outgoingEdges = new HashMap<>();
        for (ApplicationEdge edge : applicationEdgeRepository.findByApplicationVersionId(applicationVersionId)) {
            outgoingEdges.computeIfAbsent(edge.getFromStepId(), ignored -> new ArrayList<>()).add(edge);
        }
        outgoingEdges.values().forEach(edges -> edges.sort(Comparator.comparing(ApplicationEdge::getId)));

        Map<String, Object> context = new HashMap<>();
        context.put("inputs", input);
        context.put("steps", new HashMap<String, Object>());
        Map<String, Object> finalOutput = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(resolvedStartStepId);
        int executionCount = 0;

        while (!queue.isEmpty()) {
            if (++executionCount > 1000) {
                throw new IllegalStateException("Application execution exceeded the maximum step limit.");
            }

            String stepId = queue.removeFirst();
            ApplicationStepDefinition step = stepsById.get(stepId);
            if (step == null) {
                throw new IllegalStateException("Application step not found during execution: " + stepId);
            }

            String stepRunId = UUID.randomUUID().toString();
            OffsetDateTime startedAt = OffsetDateTime.now();
            Map<String, Object> stepInput = stepInput(step, context);
            applicationStepRunRepository.insertStepRun(
                    stepRunId,
                    runId,
                    step.resolvedId(),
                    ApplicationStepRunStatus.RUNNING,
                    JsonUtils.toJson(stepInput),
                    null,
                    null,
                    startedAt,
                    null
            );
            listener.stepStarted(runId, stepRunId, step, stepInput);

            boolean stepSucceeded = false;
            Object output = null;
            try {
                output = executeStep(step, stepInput, userId, context);
                stepSucceeded = true;
                Map<String, Object> stepState = new HashMap<>();
                stepState.put("input", stepInput);
                stepState.put("output", output);
                ((Map<String, Object>) context.get("steps")).put(step.resolvedId(), stepState);
                finalOutput.put(step.resolvedId(), output);
                applicationStepRunRepository.completeStepRun(
                        stepRunId,
                        ApplicationStepRunStatus.COMPLETED,
                        JsonUtils.toJson(output),
                        null,
                        OffsetDateTime.now()
                );
                listener.stepCompleted(runId, stepRunId, step, output);
            } catch (Exception exception) {
                Map<String, Object> stepState = new HashMap<>();
                stepState.put("input", stepInput);
                stepState.put("error", exception.getMessage());
                ((Map<String, Object>) context.get("steps")).put(step.resolvedId(), stepState);
                applicationStepRunRepository.completeStepRun(
                        stepRunId,
                        ApplicationStepRunStatus.FAILED,
                        null,
                        exception.getMessage(),
                        OffsetDateTime.now()
                );
                listener.stepFailed(runId, stepRunId, step, exception.getMessage());
                if (!"continue".equalsIgnoreCase(step.onError())) {
                    throw new IllegalStateException("Application step failed: " + step.resolvedId() + ": " + exception.getMessage(), exception);
                }
            }

            List<String> nextStepIds = selectNextSteps(
                    outgoingEdges.getOrDefault(step.resolvedId(), List.of()),
                    stepSucceeded,
                    output,
                    context
            );
            nextStepIds.forEach(queue::addLast);
        }

        return finalOutput;
    }

    @SuppressWarnings("unchecked")
    private ApplicationStepDefinition stepDefinition(ApplicationStep step) {
        Map<String, Object> config = parseConfig(step.getConfigJson());
        Map<String, Object> input = config.get("input") instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        return new ApplicationStepDefinition(
                step.getId(),
                step.getStepKey(),
                step.getName(),
                step.getStepType(),
                firstStringValue(config.get("toolName"), config.get("actionIdentifier")),
                stringValue(config.get("prompt")),
                input,
                config,
                stringValue(config.get("onError"))
        );
    }

    private Map<String, Object> stepInput(ApplicationStepDefinition step, Map<String, Object> context) {
        Map<String, Object> input = new HashMap<>();
        if (step.input() != null) {
            step.input().forEach((key, value) -> input.put(key, resolveValue(value, context)));
        }
        if (step.prompt() != null) {
            input.put("prompt", resolveText(step.prompt(), context));
        }
        return input;
    }

    private Object executeStep(ApplicationStepDefinition step,
                               Map<String, Object> stepInput,
                               String userId,
                               Map<String, Object> context) {
        String type = normalizeStepType(step.type());
        return switch (type) {
            case "tool" -> executeToolStep(step, stepInput, userId);
            case "llm" -> executeLlmStep(step, stepInput, userId, context);
            case "condition" -> executeConditionStep(step, context);
            default -> executeRenderableStep(step, stepInput, context);
        };
    }

    private Object executeToolStep(ApplicationStepDefinition step, Map<String, Object> stepInput, String userId) {
        if (!StringUtils.hasText(step.toolName())) {
            throw new IllegalArgumentException("Tool step is missing toolName.");
        }
        List<ToolArgument> arguments = new ArrayList<>();
        stepInput.forEach((name, value) -> arguments.add(new ToolArgument(name, value)));
        ToolResult result = toolService.execute(step.toolName(), arguments, userId);
        if (result == null || !result.success()) {
            throw new IllegalStateException(result != null ? result.content() : "Tool returned no result.");
        }
        return parseToolResult(result.content());
    }

    private Object executeLlmStep(ApplicationStepDefinition step,
                                  Map<String, Object> stepInput,
                                  String userId,
                                  Map<String, Object> context) {
        String prompt = stepInput.get("prompt") instanceof String value ? value : step.prompt();
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("LLM step is missing prompt.");
        }
        String executionId = "application-" + UUID.randomUUID();
        String task = """
                Execute this application step and return the step result.

                Step id: %s
                Step name: %s

                Step instruction:
                %s

                Application context JSON:
                %s
                """.formatted(
                step.resolvedId(),
                StringUtils.hasText(step.name()) ? step.name() : step.resolvedId(),
                prompt,
                JsonUtils.toJson(context != null ? context : Map.of())
        );
        String response = agentTaskService.executeStepTask(executionId, step.resolvedId(), userId, task);
        return parseAgentResponse(response);
    }

    private Object executeConditionStep(ApplicationStepDefinition step, Map<String, Object> context) {
        boolean passed = evaluateCondition(parseCondition(step.config().get("condition")), context, null);
        return Map.of("passed", passed);
    }

    private Object executeRenderableStep(ApplicationStepDefinition step,
                                         Map<String, Object> stepInput,
                                         Map<String, Object> context) {
        Map<String, Object> resolvedConfig = new HashMap<>();
        step.config().forEach((key, value) -> {
            if (!List.of("toolName", "actionIdentifier", "prompt", "input", "onError").contains(key)) {
                resolvedConfig.put(key, resolveValue(value, context));
            }
        });
        Map<String, Object> output = new HashMap<>();
        output.put("type", normalizeStepType(step.type()));
        output.put("name", step.name());
        output.put("config", resolvedConfig);
        output.put("input", stepInput);
        return output;
    }

    private List<String> selectNextSteps(List<ApplicationEdge> edges,
                                         boolean previousStepSucceeded,
                                         Object stepOutput,
                                         Map<String, Object> context) {
        if (edges.isEmpty()) {
            return List.of();
        }
        List<String> nextStepIds = new ArrayList<>();
        boolean matchedIfBranch = false;
        for (ApplicationEdge edge : edges) {
            String conditionType = normalizeConditionType(edge.getConditionType());
            boolean matches = switch (conditionType) {
                case "always" -> true;
                case "success" -> previousStepSucceeded;
                case "failure" -> !previousStepSucceeded;
                case "if" -> {
                    boolean result = evaluateCondition(parseCondition(edge.getConditionJson()), context, stepOutput);
                    if (result) {
                        matchedIfBranch = true;
                    }
                    yield result;
                }
                case "else" -> !matchedIfBranch;
                default -> false;
            };
            if (matches) {
                nextStepIds.add(edge.getToStepId());
            }
        }
        return nextStepIds;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new HashMap<>();
        }
        Object parsed = JsonUtils.fromJson(configJson);
        if (!(parsed instanceof Map<?, ?> map)) {
            return new HashMap<>();
        }
        return new HashMap<>((Map<String, Object>) map);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCondition(Object value) {
        if (value instanceof String json && StringUtils.hasText(json)) {
            Object parsed = JsonUtils.fromJson(json);
            if (parsed instanceof Map<?, ?> map) {
                return new HashMap<>((Map<String, Object>) map);
            }
        }
        if (value instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    private boolean evaluateCondition(Map<String, Object> condition, Map<String, Object> context, Object currentOutput) {
        String path = stringValue(condition.get("path"));
        String operator = StringUtils.hasText(stringValue(condition.get("operator")))
                ? stringValue(condition.get("operator")).trim().toLowerCase()
                : "exists";
        Object expected = condition.get("value");
        Object actual = resolveConditionValue(path, context, currentOutput);
        return switch (operator) {
            case "exists" -> actual != null;
            case "truthy" -> isTruthy(actual);
            case "equals", "eq" -> valuesEqual(actual, expected);
            case "not_equals", "neq" -> !valuesEqual(actual, expected);
            case "greater_than", "gt" -> compareNumbers(actual, expected) > 0;
            case "greater_than_or_equal", "gte" -> compareNumbers(actual, expected) >= 0;
            case "less_than", "lt" -> compareNumbers(actual, expected) < 0;
            case "less_than_or_equal", "lte" -> compareNumbers(actual, expected) <= 0;
            case "contains" -> containsValue(actual, expected);
            default -> false;
        };
    }

    private Object resolveConditionValue(String path, Map<String, Object> context, Object currentOutput) {
        if (!StringUtils.hasText(path)) {
            return currentOutput;
        }
        if (currentOutput instanceof Map<?, ?> outputMap) {
            Object currentValue = resolvePath(path, castMap(outputMap));
            if (currentValue != UNRESOLVED_TEMPLATE) {
                return currentValue;
            }
        }
        Object resolved = resolvePath(path, context);
        return resolved != UNRESOLVED_TEMPLATE ? resolved : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return new HashMap<>((Map<String, Object>) map);
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private int compareNumbers(Object actual, Object expected) {
        Double left = asNumber(actual);
        Double right = asNumber(expected);
        if (left == null || right == null) {
            return -1;
        }
        return Double.compare(left, right);
    }

    private Double asNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean containsValue(Object actual, Object expected) {
        if (actual instanceof String stringValue) {
            return expected != null && stringValue.contains(String.valueOf(expected));
        }
        if (actual instanceof List<?> listValue) {
            return listValue.stream().anyMatch(item -> valuesEqual(item, expected));
        }
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0d;
        }
        if (value instanceof String stringValue) {
            return StringUtils.hasText(stringValue) && !"false".equalsIgnoreCase(stringValue.trim());
        }
        return true;
    }

    private Object parseAgentResponse(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of("text", "");
        }
        String normalized = stripJsonFence(content.trim());
        try {
            Object parsed = JsonUtils.fromJson(normalized);
            if (parsed instanceof String parsedString && StringUtils.hasText(parsedString)) {
                String nested = stripJsonFence(parsedString.trim());
                try {
                    return JsonUtils.fromJson(nested);
                } catch (Exception ignored) {
                    return Map.of("text", parsedString);
                }
            }
            return parsed;
        } catch (Exception ignored) {
            return Map.of("text", content);
        }
    }

    private String stripJsonFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        return content.replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
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
        return StringUtils.hasText(type) ? type.trim().toLowerCase() : "view";
    }

    private String normalizeConditionType(String type) {
        return StringUtils.hasText(type) ? type.trim().toLowerCase() : "always";
    }

    private Object resolveValue(Object value, Map<String, Object> context) {
        if (value instanceof String stringValue) {
            Matcher exactMatcher = EXACT_TEMPLATE_PATTERN.matcher(stringValue);
            if (exactMatcher.matches()) {
                Object resolved = resolvePath(exactMatcher.group(1), context);
                return resolved != UNRESOLVED_TEMPLATE ? resolved : stringValue;
            }
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
        Matcher matcher = TEMPLATE_PATTERN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Object replacement = resolvePath(matcher.group(1), context);
            if (replacement == UNRESOLVED_TEMPLATE) {
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(toTemplateString(replacement)));
            }
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object resolvePath(String rawPath, Map<String, Object> context) {
        if (!StringUtils.hasText(rawPath) || context == null) {
            return UNRESOLVED_TEMPLATE;
        }
        String[] segments = rawPath.trim().split("\\.");
        Object current = context;
        for (String segment : segments) {
            if (!StringUtils.hasText(segment)) {
                return UNRESOLVED_TEMPLATE;
            }
            if (current instanceof Map<?, ?> map && map.containsKey(segment)) {
                current = map.get(segment);
            } else {
                return UNRESOLVED_TEMPLATE;
            }
        }
        return current;
    }

    private String toTemplateString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return JsonUtils.toJson(value);
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

    private static final class ApplicationStepRunStatus {
        private static final String RUNNING = "running";
        private static final String COMPLETED = "completed";
        private static final String FAILED = "failed";

        private ApplicationStepRunStatus() {
        }
    }
}
