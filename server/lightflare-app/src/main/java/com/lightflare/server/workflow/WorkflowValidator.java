package com.lightflare.server.workflow;

import com.lightflare.server.agent.tool.ToolService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WorkflowValidator {

    private final ToolService toolService;

    public void validate(WorkflowSchema schema) {
        List<String> errors = new ArrayList<>();

        if (schema == null) {
            throw new IllegalArgumentException("Workflow schema is required.");
        }
        if (schema.steps() == null || schema.steps().isEmpty()) {
            return;
        }

        Map<String, WorkflowStepDefinition> stepsById = new HashMap<>();
        for (WorkflowStepDefinition step : schema.steps()) {
            validateStepBasics(step, stepsById, errors);
        }

        validateTransitions(schema.steps(), stepsById, errors);

        if (errors.isEmpty()) {
            validateAcyclic(schema.steps(), stepsById, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid workflow schema: " + String.join("; ", errors));
        }
    }

    private void validateStepBasics(WorkflowStepDefinition step, Map<String, WorkflowStepDefinition> stepsById,
            List<String> errors) {
        if (step == null) {
            errors.add("step cannot be null");
            return;
        }
        if (!StringUtils.hasText(step.stepId())) {
            errors.add("stepId is required");
            return;
        }
        if (stepsById.putIfAbsent(step.stepId(), step) != null) {
            errors.add("duplicate stepId '" + step.stepId() + "'");
        }

        String type = step.type() != null ? step.type().toUpperCase() : "TOOL";
        switch (type) {
            case "TRIGGER", "CONDITION" -> {
            }
            case "TOOL" -> validateToolStep(step, errors);
            default -> errors.add("step '" + step.stepId() + "' has unsupported type '" + step.type() + "'");
        }
    }

    private void validateToolStep(WorkflowStepDefinition step, List<String> errors) {
        if (!StringUtils.hasText(step.actionIdentifier())) {
            errors.add("tool step '" + step.stepId() + "' requires actionIdentifier");
            return;
        }
        try {
            toolService.findDefinition(step.actionIdentifier());
        } catch (Exception e) {
            errors.add("tool step '" + step.stepId() + "' references unknown tool '" + step.actionIdentifier() + "'");
        }
    }

    private void validateTransitions(List<WorkflowStepDefinition> steps,
            Map<String, WorkflowStepDefinition> stepsById, List<String> errors) {
        for (WorkflowStepDefinition step : steps) {
            if (step == null || step.transitions() == null) {
                continue;
            }
            for (int i = 0; i < step.transitions().size(); i++) {
                WorkflowStepTransition transition = step.transitions().get(i);
                if (transition == null) {
                    errors.add("step '" + step.stepId() + "' has null transition");
                    continue;
                }
                if (!StringUtils.hasText(transition.conditionExpression())) {
                    errors.add("step '" + step.stepId() + "' has transition without conditionExpression");
                }
                String target = transition.targetStepId();
                if (!"end".equalsIgnoreCase(target) && !stepsById.containsKey(target)) {
                    errors.add("step '" + step.stepId() + "' targets missing step '" + target + "'");
                }
                if (isDefaultTransition(transition) && i < step.transitions().size() - 1) {
                    errors.add("step '" + step.stepId() + "' default transition must be last");
                }
            }
        }
    }

    private void validateAcyclic(List<WorkflowStepDefinition> steps,
            Map<String, WorkflowStepDefinition> stepsById, List<String> errors) {
        Map<String, VisitState> states = new HashMap<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        for (WorkflowStepDefinition step : steps) {
            if (step != null && hasCycle(step.stepId(), stepsById, states, path)) {
                errors.add("workflow graph contains a cycle involving step '" + step.stepId() + "'");
                return;
            }
        }
    }

    private boolean hasCycle(String stepId, Map<String, WorkflowStepDefinition> stepsById,
            Map<String, VisitState> states, ArrayDeque<String> path) {
        VisitState state = states.get(stepId);
        if (state == VisitState.VISITING) {
            return true;
        }
        if (state == VisitState.VISITED) {
            return false;
        }

        states.put(stepId, VisitState.VISITING);
        path.push(stepId);
        WorkflowStepDefinition step = stepsById.get(stepId);
        if (step != null && step.transitions() != null) {
            Set<String> targets = new HashSet<>();
            for (WorkflowStepTransition transition : step.transitions()) {
                if (transition == null || "end".equalsIgnoreCase(transition.targetStepId())) {
                    continue;
                }
                if (targets.add(transition.targetStepId())
                        && hasCycle(transition.targetStepId(), stepsById, states, path)) {
                    return true;
                }
            }
        }
        path.pop();
        states.put(stepId, VisitState.VISITED);
        return false;
    }

    private boolean isDefaultTransition(WorkflowStepTransition transition) {
        String condition = transition.conditionExpression();
        return "default".equalsIgnoreCase(condition) || "else".equalsIgnoreCase(condition);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
