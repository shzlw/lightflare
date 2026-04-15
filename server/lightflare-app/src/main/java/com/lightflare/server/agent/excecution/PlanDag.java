package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlanDag {

    private final List<LLMPlanResponse.PlanStep> stepsInOrder;
    private final Map<String, PlanNode> nodesById;

    PlanDag(List<LLMPlanResponse.PlanStep> stepsInOrder, Map<String, PlanNode> nodesById) {
        this.stepsInOrder = List.copyOf(stepsInOrder);
        this.nodesById = new LinkedHashMap<>(nodesById);
    }

    List<LLMPlanResponse.PlanStep> steps() {
        return stepsInOrder;
    }

    boolean hasPendingSteps() {
        return stepsInOrder.stream()
                .anyMatch(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING);
    }

    boolean hasRunnablePendingSteps() {
        return !readySteps().isEmpty();
    }

    List<LLMPlanResponse.PlanStep> readySteps() {
        return stepsInOrder.stream()
                .filter(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)
                .filter(this::dependenciesCompleted)
                .toList();
    }

    void markRunning(List<LLMPlanResponse.PlanStep> parallelSteps) {
        if (CollectionUtils.isEmpty(parallelSteps)) {
            return;
        }
        parallelSteps.forEach(step -> step.setStatus(LLMPlanResponse.PlanStep.Status.RUNNING));
    }

    void updateStepStatus(String stepId, LLMPlanResponse.PlanStep.Status status) {
        PlanNode node = nodesById.get(stepId);
        if (node == null || node.step() == null || status == null) {
            return;
        }
        node.step().setStatus(status);
    }

    LLMPlanResponse.PlanStep stepById(String stepId) {
        PlanNode node = nodesById.get(stepId);
        return node != null ? node.step() : null;
    }

    List<String> unresolvedDependencies(LLMPlanResponse.PlanStep step) {
        PlanNode node = step != null ? nodesById.get(step.getId()) : null;
        if (node == null) {
            return List.of();
        }
        return node.dependencies().stream()
                .filter(StringUtils::hasText)
                .filter(dependencyId -> {
                    PlanNode dependencyNode = nodesById.get(dependencyId);
                    return dependencyNode == null
                            || dependencyNode.step().getStatus() != LLMPlanResponse.PlanStep.Status.COMPLETED;
                })
                .toList();
    }

    private boolean dependenciesCompleted(LLMPlanResponse.PlanStep step) {
        PlanNode node = step != null ? nodesById.get(step.getId()) : null;
        if (node == null || CollectionUtils.isEmpty(node.dependencies())) {
            return true;
        }

        for (String dependencyId : node.dependencies()) {
            if (!StringUtils.hasText(dependencyId)) {
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
            List<String> dependencies,
            List<String> dependents
    ) {
    }
}
