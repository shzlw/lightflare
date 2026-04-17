package com.lightflare.server.workflow;

import com.lightflare.server.agent.tool.ToolService;
import com.lightflare.server.tools.core.ToolArgument;
import com.lightflare.server.tools.core.ToolResult;
import com.lightflare.server.utils.JsonUtils;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final WorkflowStepExecutionRepository stepExecutionRepository;
    private final WorkflowInputResolver inputResolver;
    private final WorkflowValidator workflowValidator;
    private final ToolService toolService;
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Triggers the execution of a workflow.
     * 
     * @param workflowId The ID of the workflow to run.
     * @return The execution ID.
     */
    public String execute(String workflowId) {
        return execute(workflowId, new HashMap<>(), null);
    }

    public String execute(String workflowId, Map<String, Object> initialData) {
        return execute(workflowId, initialData, null);
    }

    public String execute(String workflowId, Map<String, Object> initialData, String startStepId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        WorkflowSchema schema = JsonUtils.fromJson(workflow.getSchemaDefinition(), WorkflowSchema.class);
        return execute(workflowId, schema, initialData, startStepId);
    }

    public String execute(String workflowId, WorkflowSchema schema, Map<String, Object> initialData,
            String startStepId) {
        workflowValidator.validate(schema);

        String executionId = UUID.randomUUID().toString();
        executionRepository.insertExecution(executionId, workflowId, schema.version(), "RUNNING", OffsetDateTime.now());

        try {
            runWorkflow(executionId, schema, initialData, startStepId);
            executionRepository.updateStatus(executionId, "COMPLETED", OffsetDateTime.now());
        } catch (Exception e) {
            log.error("Workflow execution {} failed: {}", executionId, e.getMessage(), e);
            executionRepository.updateStatus(executionId, "FAILED", OffsetDateTime.now());
        }

        return executionId;
    }

    /**
     * Executes a single step definition for testing purposes.
     */
    public Object testStep(WorkflowStepDefinition step, Map<String, Object> mockContext) {
        Map<String, Object> resolvedInputs = inputResolver.resolve(step.inputMapping(),
                mockContext != null ? mockContext : new HashMap<>());
        return executeStepAction(step, resolvedInputs);
    }

    private void runWorkflow(String executionId, WorkflowSchema schema, Map<String, Object> initialData,
            String startStepId) {
        Map<String, Object> globalContext = new HashMap<>();

        if (schema.steps() == null || schema.steps().isEmpty()) {
            log.warn("Workflow has no steps to execute.");
            return;
        }

        WorkflowStepDefinition currentStep;
        if (startStepId != null) {
            // Jump to explicit entry point
            currentStep = findStepDefinition(startStepId, schema);
        } else {
            // Default logic: Find first TRIGGER step, otherwise default to first step in
            // list
            currentStep = schema.steps().stream()
                    .filter(s -> "TRIGGER".equalsIgnoreCase(s.type()))
                    .findFirst()
                    .orElse(schema.steps().get(0));
        }

        int executedSteps = 0;
        int maxSteps = schema.steps().size();
        while (currentStep != null) {
            if (++executedSteps > maxSteps) {
                throw new IllegalStateException("Workflow exceeded DAG step limit; graph may contain a cycle.");
            }

            String stepId = currentStep.stepId();
            String stepExecId = null;

            try {
                Object finalResult;
                if ("TRIGGER".equalsIgnoreCase(currentStep.type())) {
                    // Trigger step simply outputs the initial data provided to the engine
                    finalResult = initialData != null ? initialData : new HashMap<>();

                    // Record trigger step start/end
                    stepExecId = UUID.randomUUID().toString();
                    stepExecutionRepository.insertStepExecution(
                            stepExecId, executionId, stepId, schema.version(), "RUNNING", JsonUtils.toJson(finalResult),
                            OffsetDateTime.now());
                    stepExecutionRepository.updateStepResult(
                            stepExecId, "SUCCESS", JsonUtils.toJson(finalResult), null, OffsetDateTime.now());
                } else {
                    // Resolve Inputs using context from previous steps
                    Map<String, Object> resolvedInputs = inputResolver.resolve(currentStep.inputMapping(),
                            globalContext);

                    // 2. Record Start of Step
                    stepExecId = UUID.randomUUID().toString();
                    stepExecutionRepository.insertStepExecution(
                            stepExecId, executionId, stepId, schema.version(), "RUNNING",
                            JsonUtils.toJson(resolvedInputs),
                            OffsetDateTime.now());

                    // 3. Execute Action
                    Object rawResult = executeStepAction(currentStep, resolvedInputs);

                    // 4. Process Output Mapping (Pruning/Filtering)
                    finalResult = processOutputMapping(currentStep, rawResult, globalContext);

                    // 5. Update Context & Record Success
                    stepExecutionRepository.updateStepResult(
                            stepExecId, "SUCCESS", JsonUtils.toJson(finalResult), null, OffsetDateTime.now());
                }

                // Update context for following steps
                globalContext.put(stepId, Map.of("output", finalResult));

                // 6. Determine Next Step via Transitions
                currentStep = evaluateTransitions(currentStep, finalResult, schema, globalContext);

            } catch (Exception e) {
                if (stepExecId != null) {
                    stepExecutionRepository.updateStepFailure(
                            stepExecId, "FAILED", e.getMessage(), OffsetDateTime.now());
                }
                throw new RuntimeException("Error in step '" + stepId + "': " + e.getMessage(), e);
            }
        }
    }

    private Object processOutputMapping(WorkflowStepDefinition step, Object rawOutput,
            Map<String, Object> globalContext) {
        if (step.outputMapping() == null || step.outputMapping().isEmpty()) {
            return rawOutput;
        }

        log.info("Applying output mapping for step: {}", step.stepId());
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("output", rawOutput);
        evalContext.setVariable("steps", globalContext);

        Map<String, Object> mappedOutput = new HashMap<>();
        step.outputMapping().forEach((key, expression) -> {
            if (expression instanceof String exprStr) {
                try {
                    // We assume output mapping strings are SpEL expressions
                    Object val = parser.parseExpression(exprStr).getValue(evalContext);
                    mappedOutput.put(key, val);
                } catch (Exception e) {
                    // Fallback to the literal string if parsing fails
                    mappedOutput.put(key, exprStr);
                }
            } else {
                mappedOutput.put(key, expression);
            }
        });
        return mappedOutput;
    }

    private Object executeStepAction(WorkflowStepDefinition step, Map<String, Object> resolvedInputs) {
        String type = step.type() != null ? step.type().toUpperCase() : "TOOL";

        return switch (type) {
            case "TOOL" -> executeToolStep(step.actionIdentifier(), resolvedInputs);
            case "CONDITION" -> resolvedInputs; // Simple passthrough for condition-only steps
            default -> throw new UnsupportedOperationException("Unsupported step type: " + type);
        };
    }

    private Object executeToolStep(String toolName, Map<String, Object> inputs) {
        List<ToolArgument> args = new ArrayList<>();
        if (inputs != null) {
            inputs.forEach((k, v) -> args.add(new ToolArgument(k, v)));
        }

        log.info("Executing Tool Step: {} with args: {}", toolName, inputs);
        ToolResult result = toolService.execute(toolName, args);

        if (!result.success()) {
            throw new RuntimeException("Tool '" + toolName + "' failed: " + result.content());
        }

        // Try to parse as JSON if possible, otherwise return raw content
        try {
            return JsonUtils.fromJson(result.content());
        } catch (Exception e) {
            return Map.of("text", result.content());
        }
    }

    private WorkflowStepDefinition evaluateTransitions(WorkflowStepDefinition current, Object output,
            WorkflowSchema schema, Map<String, Object> globalContext) {
        if (current.transitions() == null || current.transitions().isEmpty()) {
            return null;
        }

        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("output", output);
        evalContext.setVariable("steps", globalContext);

        for (WorkflowStepTransition transition : current.transitions()) {
            String condition = transition.conditionExpression();

            if ("default".equalsIgnoreCase(condition) || "else".equalsIgnoreCase(condition)) {
                return findStepDefinition(transition.targetStepId(), schema);
            }

            try {
                Boolean match = parser.parseExpression(condition).getValue(evalContext, Boolean.class);
                if (Boolean.TRUE.equals(match)) {
                    return findStepDefinition(transition.targetStepId(), schema);
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate transition condition '{}': {}", condition, e.getMessage());
            }
        }

        return null; // End workflow if no transitions match
    }

    private WorkflowStepDefinition findStepDefinition(String id, WorkflowSchema schema) {
        if (id == null || "end".equalsIgnoreCase(id)) {
            return null;
        }
        return schema.steps().stream()
                .filter(s -> id.equals(s.stepId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target step not found: " + id));
    }
}
