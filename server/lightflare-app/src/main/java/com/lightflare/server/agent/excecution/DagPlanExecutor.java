package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.config.AgentProperties;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DagPlanExecutor {

    private static final String LOG_STAGE = "DAG_SCHED";

    private final AgentProperties agentProperties;
    private final StepExecutionService stepExecutionService;
    private final PlanStepFormatter planStepFormatter;
    @Qualifier("dagPlanTaskExecutor")
    private final Executor dagPlanTaskExecutor;

    public List<LLMPlanResponse.PlanStep> selectNextParallelSteps(AgentRunContext runContext, PlanDag planDag) {
        List<LLMPlanResponse.PlanStep> readySteps = planDag.readySteps();
        if (readySteps.isEmpty()) {
            log.info("[{}][READY_NONE] sessionId={}, pendingStepCount={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    planDag.steps().stream()
                            .filter(step -> step != null && step.getStatus() == LLMPlanResponse.PlanStep.Status.PENDING)
                            .count());
            return List.of();
        }

        List<LLMPlanResponse.PlanStep> parallelSteps = selectParallelSteps(readySteps);
        planDag.markRunning(parallelSteps);
        log.info("[{}][PARALLEL_START] sessionId={}, readyStepCount={}, parallelStepCount={}, parallelStepIds={}",
                LOG_STAGE,
                runContext.executionId(),
                readySteps.size(),
                parallelSteps.size(),
                parallelSteps.stream().map(LLMPlanResponse.PlanStep::getId).toList());
        return parallelSteps;
    }

    public void markBlockedStepsAsFailed(PlanDag planDag,
                                         List<String> executionLog,
                                         AgentExecutionListener listener,
                                         String executionId) {
        for (LLMPlanResponse.PlanStep step : planDag.steps()) {
            if (step == null || step.getStatus() != LLMPlanResponse.PlanStep.Status.PENDING) {
                continue;
            }

            List<String> unresolvedDependencies = planDag.unresolvedDependencies(step);
            planDag.updateStepStatus(step.getId(), LLMPlanResponse.PlanStep.Status.FAILED);
            executionLog.add(planStepFormatter.formatStepEntry(
                    step,
                    "STEP_STATUS",
                    "Status=FAILED unresolvedDependencies=" + unresolvedDependencies
            ));
            listener.onStepCompleted(
                    executionId,
                    clonePlanStep(step),
                    LLMPlanResponse.PlanStep.Status.FAILED.name(),
                    null,
                    List.of(planStepFormatter.formatStepEntry(
                            step,
                            "STEP_STATUS",
                            "Status=FAILED unresolvedDependencies=" + unresolvedDependencies
                    ))
            );
            log.info("[{}][BLOCKED_STEP_FAILED] stepId={}, unresolvedDependencies={}",
                    LOG_STAGE,
                    step.getId(), unresolvedDependencies);
        }
    }

    private List<LLMPlanResponse.PlanStep> selectParallelSteps(List<LLMPlanResponse.PlanStep> readySteps) {
        List<LLMPlanResponse.PlanStep> exclusiveReady = readySteps.stream()
                .filter(step -> !step.isParallelizable())
                .toList();
        if (!exclusiveReady.isEmpty()) {
            log.info("[{}][PARALLEL_SELECT] mode=exclusive, selectedStepId={}, readyStepIds={}",
                    LOG_STAGE,
                    exclusiveReady.getFirst().getId(),
                    readySteps.stream().map(LLMPlanResponse.PlanStep::getId).toList());
            return List.of(exclusiveReady.getFirst());
        }

        List<LLMPlanResponse.PlanStep> parallelSteps = readySteps.stream()
                .limit(Math.max(1, agentProperties.getMaxParallelSteps()))
                .toList();
        log.info("[{}][PARALLEL_SELECT] mode=parallel, parallelStepCount={}, selectedStepIds={}, readyStepIds={}",
                LOG_STAGE,
                parallelSteps.size(),
                parallelSteps.stream().map(LLMPlanResponse.PlanStep::getId).toList(),
                readySteps.stream().map(LLMPlanResponse.PlanStep::getId).toList());
        return parallelSteps;
    }

    public List<StepExecutionResult> executeParallelSteps(AgentExecutionState state,
                                                          List<LLMPlanResponse.PlanStep> parallelSteps) {
        StepExecutionContext executionContext = new StepExecutionContext(
                state.getRunContext().executionId(),
                state.getRunContext().userId(),
                state.getRunContext().task(),
                state.getConversationContext().promptMemories(),
                state.getRunContext().tools(),
                state.getSelectedSkill() != null ? state.getSelectedSkill().getName() : null,
                state.getSelectedSkill() != null ? state.getSelectedSkill().getContent() : null,
                state.getExecutionLog()
        );

        List<CompletableFuture<StepExecutionResult>> futures = parallelSteps.stream()
                .map(step -> CompletableFuture.supplyAsync(
                        () -> stepExecutionService.executeStepWithRetries(
                                state.getRunContext(),
                                executionContext,
                                clonePlanStep(step),
                                state.getListener()
                        ),
                        dagPlanTaskExecutor
                ))
                .toList();

        try {
            List<StepExecutionResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            log.info("[{}][PARALLEL_DONE] sessionId={}, parallelStepCount={}, resultStatuses={}",
                    LOG_STAGE,
                    state.getRunContext().executionId(),
                    parallelSteps.size(),
                    results.stream().collect(Collectors.toMap(
                            StepExecutionResult::stepId,
                            StepExecutionResult::status
                    )));
            return results;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Concurrent plan execution failed: " + cause.getMessage(), cause);
        }
    }

    public AppliedStepResults applyParallelStepResults(AgentExecutionState state,
                                                       List<LLMPlanResponse.PlanStep> parallelSteps,
                                                       List<StepExecutionResult> results) {
        Map<String, Integer> planOrder = new HashMap<>();
        List<LLMPlanResponse.PlanStep> steps = state.getPlanDag().steps();
        for (int i = 0; i < steps.size(); i++) {
            planOrder.put(steps.get(i).getId(), i);
        }

        List<StepExecutionResult> orderedResults = results.stream()
                .sorted(Comparator.comparingInt(result -> planOrder.getOrDefault(result.stepId(), Integer.MAX_VALUE)))
                .toList();

        String userMessage = null;
        PendingUserInputRequest pendingUserInputRequest = null;
        for (StepExecutionResult result : orderedResults) {
            LLMPlanResponse.PlanStep step = state.getPlanDag().stepById(result.stepId());
            if (step == null) {
                continue;
            }
            state.getPlanDag().updateStepStatus(result.stepId(), result.status());
            state.getExecutionLog().addAll(result.executionLogEntries());
            if (userMessage == null && StringUtils.hasText(result.userMessage())) {
                userMessage = result.userMessage();
            }
            if (pendingUserInputRequest == null && result.pendingUserInputRequest() != null) {
                pendingUserInputRequest = result.pendingUserInputRequest();
            }
        }

        for (LLMPlanResponse.PlanStep step : parallelSteps) {
            if (step.getStatus() == LLMPlanResponse.PlanStep.Status.RUNNING) {
                state.getPlanDag().updateStepStatus(step.getId(), LLMPlanResponse.PlanStep.Status.FAILED);
                state.getExecutionLog().add(planStepFormatter.formatStepEntry(
                        step,
                        "STEP_STATUS",
                        "Status=FAILED reason=missing-parallel-step-result"
                ));
            }
        }

        log.info("[{}][PARALLEL_MERGE] mergedStepIds={}, userMessagePresent={}, executionLogSize={}",
                LOG_STAGE,
                orderedResults.stream().map(StepExecutionResult::stepId).toList(),
                userMessage != null,
                state.getExecutionLog().size());

        return new AppliedStepResults(userMessage, pendingUserInputRequest);
    }
    private LLMPlanResponse.PlanStep clonePlanStep(LLMPlanResponse.PlanStep step) {
        LLMPlanResponse.PlanStep copy = new LLMPlanResponse.PlanStep();
        copy.setId(step.getId());
        copy.setContent(step.getContent());
        copy.setToolCategory(step.getToolCategory());
        copy.setDependsOn(CollectionUtils.isEmpty(step.getDependsOn()) ? List.of() : List.copyOf(step.getDependsOn()));
        copy.setParallelizable(step.isParallelizable());
        copy.setStatus(step.getStatus());
        return copy;
    }
}
