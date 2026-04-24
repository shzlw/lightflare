package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.plan.AgentPlanner;
import com.lightflare.server.config.AgentProperties;
import com.lightflare.server.harness.core.execution.GeneratedArtifact;
import com.lightflare.server.harness.core.execution.ResponseResolution;
import com.lightflare.server.harness.core.execution.ResponseResolutionResult;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseResolver {

    private static final String LOG_STAGE = "RESPONSE_RESOLVE";

    private final AgentPlanner agentPlanner;
    private final AgentProperties agentProperties;

    public String resolve(HarnessRunContext runContext,
                          List<LLMPlanResponse.PlanStep> steps,
                          List<String> executionLog) {
        return resolveWithMetadata(runContext, steps, executionLog).response();
    }

    public ResponseResolutionResult resolveWithMetadata(HarnessRunContext runContext,
                                                List<LLMPlanResponse.PlanStep> steps,
                                                List<String> executionLog) {
        ResponseResolutionResult candidateResult = agentPlanner.composeResponse(
                runContext.executionId(),
                runContext.executionType(),
                runContext.userId(),
                runContext.task(),
                steps,
                executionLog,
                null,
                null
        );
        String candidateResponse = candidateResult != null ? candidateResult.response() : null;
        if (!StringUtils.hasText(candidateResponse)) {
            return new ResponseResolutionResult(buildStatusFallback(runContext, steps), false);
        }
        List<GeneratedArtifact> candidateArtifacts = candidateResult != null ? candidateResult.artifacts() : List.of();

        int maxResolutionRounds = Math.max(0, agentProperties.getMaxResponseResolutionRounds());
        for (int resolutionRound = 0; resolutionRound <= maxResolutionRounds; resolutionRound++) {
            ResponseResolution resolution = agentPlanner.reviewResponse(
                    runContext.executionId(),
                    runContext.executionType(),
                    runContext.userId(),
                    runContext.task(),
                    steps,
                    executionLog,
                    candidateResponse
            );
            if (resolution == null || resolution.getOutcome() == null) {
                log.warn("[{}][REVIEW_EMPTY] sessionId={}, resolutionRound={}",
                        LOG_STAGE,
                        runContext.executionId(),
                        resolutionRound);
                return new ResponseResolutionResult(candidateResponse, false);
            }

            log.info("[{}][REVIEW] sessionId={}, resolutionRound={}, outcome={}",
                    LOG_STAGE,
                    runContext.executionId(),
                    resolutionRound,
                    resolution.getOutcome());

            switch (resolution.getOutcome()) {
                case ACCEPT -> {
                    return new ResponseResolutionResult(candidateResponse, false, candidateArtifacts);
                }
                case ASK_FOR_MORE_INFO -> {
                    if (StringUtils.hasText(resolution.getUserMessage())) {
                        return new ResponseResolutionResult(resolution.getUserMessage(), true);
                    }
                    return new ResponseResolutionResult(candidateResponse, false, candidateArtifacts);
                }
                case CANNOT_COMPLETE -> {
                    if (StringUtils.hasText(resolution.getUserMessage())) {
                        return new ResponseResolutionResult(resolution.getUserMessage(), false);
                    }
                    return new ResponseResolutionResult(candidateResponse, false, candidateArtifacts);
                }
                case REFINE_RESPONSE -> {
                    if (resolutionRound >= maxResolutionRounds) {
                        log.info("[{}][RESOLUTION_LIMIT_REACHED] sessionId={}, maxResolutionRounds={}",
                                LOG_STAGE,
                                runContext.executionId(),
                                maxResolutionRounds);
                        if (StringUtils.hasText(resolution.getUserMessage())) {
                            return new ResponseResolutionResult(resolution.getUserMessage(), false);
                        }
                        return new ResponseResolutionResult(candidateResponse, false);
                    }

                    ResponseResolutionResult refinedResult = agentPlanner.composeResponse(
                            runContext.executionId(),
                            runContext.executionType(),
                            runContext.userId(),
                            runContext.task(),
                            steps,
                            executionLog,
                            candidateResponse,
                            resolution.getFeedback()
                    );
                    String refinedResponse = refinedResult != null ? refinedResult.response() : null;
                    if (!StringUtils.hasText(refinedResponse)) {
                        log.warn("[{}][REFINEMENT_EMPTY] sessionId={}, resolutionRound={}",
                                LOG_STAGE,
                                runContext.executionId(),
                                resolutionRound + 1);
                        return new ResponseResolutionResult(candidateResponse, false);
                    }
                    candidateResponse = refinedResponse;
                    candidateArtifacts = refinedResult != null ? refinedResult.artifacts() : List.of();
                }
            }
        }

        return new ResponseResolutionResult(candidateResponse, false, candidateArtifacts);
    }

    private String buildStatusFallback(HarnessRunContext runContext, List<LLMPlanResponse.PlanStep> steps) {
        long completedSteps = steps.stream()
                .filter(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.COMPLETED)
                .count();
        long failedSteps = steps.stream()
                .filter(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.FAILED)
                .count();

        if (completedSteps > 0 && failedSteps == 0) {
            log.info("[{}][STATUS_FALLBACK] sessionId={}, result=success, completedSteps={}, failedSteps={}",
                    LOG_STAGE, runContext.executionId(), completedSteps, failedSteps);
            return "Completed all planned steps.";
        }
        if (completedSteps > 0) {
            log.info("[{}][STATUS_FALLBACK] sessionId={}, result=partial, completedSteps={}, failedSteps={}",
                    LOG_STAGE, runContext.executionId(), completedSteps, failedSteps);
            return "Completed " + completedSteps + " planned steps, but " + failedSteps + " steps failed.";
        }
        log.info("[{}][STATUS_FALLBACK] sessionId={}, result=failure, completedSteps=0, failedSteps={}",
                LOG_STAGE, runContext.executionId(), failedSteps);
        return "The plan could not be completed.";
    }
}
