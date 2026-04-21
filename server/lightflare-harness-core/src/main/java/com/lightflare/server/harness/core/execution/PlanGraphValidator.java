package com.lightflare.server.harness.core.execution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlanGraphValidator {

    public PlanDag buildValidatedDag(List<LLMPlanResponse.PlanStep> steps) {
        if (steps == null) {
            throw new IllegalStateException("Steps must not be null");
        }
        Map<String, LLMPlanResponse.PlanStep> stepsById = new LinkedHashMap<>();
        for (LLMPlanResponse.PlanStep step : steps) {
            if (step == null || isBlank(step.getId())) {
                throw new IllegalStateException("Planner returned a step without an id");
            }
            if (stepsById.putIfAbsent(step.getId(), step) != null) {
                throw new IllegalStateException("Planner returned duplicate step id: " + step.getId());
            }
        }

        Map<String, Integer> inboundCount = new HashMap<>();
        Map<String, List<String>> outgoingEdges = new HashMap<>();
        stepsById.keySet().forEach(stepId -> {
            inboundCount.put(stepId, 0);
            outgoingEdges.put(stepId, new ArrayList<>());
        });

        for (LLMPlanResponse.PlanStep step : stepsById.values()) {
            Set<String> uniqueDependencies = new HashSet<>();
            for (String dependencyId : sanitizeDependencies(step.getDependsOn())) {
                if (!stepsById.containsKey(dependencyId)) {
                    throw new IllegalStateException("Planner returned unknown dependency '" + dependencyId
                            + "' for step " + step.getId());
                }
                if (!uniqueDependencies.add(dependencyId)) {
                    continue;
                }
                inboundCount.computeIfPresent(step.getId(), (ignored, count) -> count + 1);
                outgoingEdges.get(dependencyId).add(step.getId());
            }
        }

        Deque<String> ready = new ArrayDeque<>();
        inboundCount.forEach((stepId, count) -> {
            if (count == 0) {
                ready.add(stepId);
            }
        });

        int visited = 0;
        while (!ready.isEmpty()) {
            String stepId = ready.removeFirst();
            visited++;
            for (String dependentId : outgoingEdges.getOrDefault(stepId, List.of())) {
                int remaining = inboundCount.computeIfPresent(dependentId, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.addLast(dependentId);
                }
            }
        }

        if (visited != stepsById.size()) {
            throw new IllegalStateException("Planner returned cyclic step dependencies");
        }

        Map<String, PlanDag.PlanNode> nodesById = new LinkedHashMap<>();
        for (LLMPlanResponse.PlanStep step : steps) {
            List<String> dependencies = sanitizeDependencies(step.getDependsOn()).stream()
                    .distinct()
                    .toList();
            nodesById.put(step.getId(), new PlanDag.PlanNode(step, dependencies));
        }
        return new PlanDag(List.copyOf(steps), nodesById);
    }

    private List<String> sanitizeDependencies(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return List.of();
        }
        return dependencies.stream()
                .filter(dependency -> !isBlank(dependency))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
