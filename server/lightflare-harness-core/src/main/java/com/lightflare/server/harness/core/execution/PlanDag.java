package com.lightflare.server.harness.core.execution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanDag {

    private final List<LLMPlanResponse.PlanStep> stepsInOrder;
    private final Map<String, PlanNode> nodesById;

    public PlanDag(List<LLMPlanResponse.PlanStep> stepsInOrder, Map<String, PlanNode> nodesById) {
        this.stepsInOrder = List.copyOf(stepsInOrder);
        this.nodesById = new LinkedHashMap<>(nodesById);
    }

    public List<LLMPlanResponse.PlanStep> steps() {
        return stepsInOrder;
    }

    public boolean hasPendingSteps() {
        return stepsInOrder.stream()
                .anyMatch(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING);
    }

    public boolean hasRunnablePendingSteps() {
        return !readySteps().isEmpty();
    }

    public List<LLMPlanResponse.PlanStep> readySteps() {
        return stepsInOrder.stream()
                .filter(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)
                .filter(this::dependenciesCompleted)
                .toList();
    }

    public void markRunning(List<LLMPlanResponse.PlanStep> parallelSteps) {
        if (parallelSteps == null || parallelSteps.isEmpty()) {
            return;
        }
        parallelSteps.forEach(step -> step.setStatus(LLMPlanResponse.PlanStep.Status.RUNNING));
    }

    public void updateStepStatus(String stepId, LLMPlanResponse.PlanStep.Status status) {
        PlanNode node = nodesById.get(stepId);
        if (node == null || node.step() == null || status == null) {
            return;
        }
        node.step().setStatus(status);
    }

    public LLMPlanResponse.PlanStep stepById(String stepId) {
        PlanNode node = nodesById.get(stepId);
        return node != null ? node.step() : null;
    }

    public List<String> unresolvedDependencies(LLMPlanResponse.PlanStep step) {
        PlanNode node = step != null ? nodesById.get(step.getId()) : null;
        if (node == null) {
            return List.of();
        }
        return node.dependencies().stream()
                .filter(dependencyId -> dependencyId != null && !dependencyId.isBlank())
                .filter(dependencyId -> {
                    PlanNode dependencyNode = nodesById.get(dependencyId);
                    return dependencyNode == null
                            || dependencyNode.step().getStatus() != LLMPlanResponse.PlanStep.Status.COMPLETED;
                })
                .toList();
    }

    private boolean dependenciesCompleted(LLMPlanResponse.PlanStep step) {
        PlanNode node = step != null ? nodesById.get(step.getId()) : null;
        if (node == null || node.dependencies() == null || node.dependencies().isEmpty()) {
            return true;
        }

        for (String dependencyId : node.dependencies()) {
            if (dependencyId == null || dependencyId.isBlank()) {
                continue;
            }
            PlanNode dependencyNode = nodesById.get(dependencyId);
            if (dependencyNode == null
                    || dependencyNode.step().getStatus() != LLMPlanResponse.PlanStep.Status.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    record PlanNode(
            LLMPlanResponse.PlanStep step,
            List<String> dependencies
    ) {
    }
}
