package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.agent.plan.AgentPlanner;
import com.lightflare.server.config.AgentProperties;
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

    public String resolve(AgentRunContext runContext,
                          List<LLMPlanResponse.PlanStep> steps,
                          List<String> executionLog) {
        String candidateResponse = agentPlanner.composeResponse(
                runContext.sessionId(),
                runContext.userId(),
                runContext.task(),
                steps,
                executionLog,
                null,
                null
        );
        if (!StringUtils.hasText(candidateResponse)) {
            return buildStatusFallback(runContext, steps);
        }

        int maxResolutionRounds = Math.max(0, agentProperties.getMaxResponseResolutionRounds());
        for (int resolutionRound = 0; resolutionRound <= maxResolutionRounds; resolutionRound++) {
            ResponseResolution resolution = agentPlanner.reviewResponse(
                    runContext.sessionId(),
                    runContext.userId(),
                    runContext.task(),
                    steps,
                    executionLog,
                    candidateResponse
            );
            if (resolution == null || resolution.getOutcome() == null) {
                log.warn("[{}][REVIEW_EMPTY] sessionId={}, resolutionRound={}",
                        LOG_STAGE,
                        runContext.sessionId(),
                        resolutionRound);
                return candidateResponse;
            }

            log.info("[{}][REVIEW] sessionId={}, resolutionRound={}, outcome={}",
                    LOG_STAGE,
                    runContext.sessionId(),
                    resolutionRound,
                    resolution.getOutcome());

            switch (resolution.getOutcome()) {
                case ACCEPT -> {
                    return candidateResponse;
                }
                case ASK_FOR_MORE_INFO, CANNOT_COMPLETE -> {
                    if (StringUtils.hasText(resolution.getUserMessage())) {
                        return resolution.getUserMessage();
                    }
                    return candidateResponse;
                }
                case REFINE_RESPONSE -> {
                    if (resolutionRound >= maxResolutionRounds) {
                        log.info("[{}][RESOLUTION_LIMIT_REACHED] sessionId={}, maxResolutionRounds={}",
                                LOG_STAGE,
                                runContext.sessionId(),
                                maxResolutionRounds);
                        if (StringUtils.hasText(resolution.getUserMessage())) {
                            return resolution.getUserMessage();
                        }
                        return candidateResponse;
                    }

                    String refinedResponse = agentPlanner.composeResponse(
                            runContext.sessionId(),
                            runContext.userId(),
                            runContext.task(),
                            steps,
                            executionLog,
                            candidateResponse,
                            resolution.getFeedback()
                    );
                    if (!StringUtils.hasText(refinedResponse)) {
                        log.warn("[{}][REFINEMENT_EMPTY] sessionId={}, resolutionRound={}",
                                LOG_STAGE,
                                runContext.sessionId(),
                                resolutionRound + 1);
                        return candidateResponse;
                    }
                    candidateResponse = refinedResponse;
                }
            }
        }

        return candidateResponse;
    }

    private String buildStatusFallback(AgentRunContext runContext, List<LLMPlanResponse.PlanStep> steps) {
        long completedSteps = steps.stream()
                .filter(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.COMPLETED)
                .count();
        long failedSteps = steps.stream()
                .filter(step -> step.getStatus() == LLMPlanResponse.PlanStep.Status.FAILED)
                .count();

        if (completedSteps > 0 && failedSteps == 0) {
            log.info("[{}][STATUS_FALLBACK] sessionId={}, result=success, completedSteps={}, failedSteps={}",
                    LOG_STAGE, runContext.sessionId(), completedSteps, failedSteps);
            return "Completed all planned steps.";
        }
        if (completedSteps > 0) {
            log.info("[{}][STATUS_FALLBACK] sessionId={}, result=partial, completedSteps={}, failedSteps={}",
                    LOG_STAGE, runContext.sessionId(), completedSteps, failedSteps);
            return "Completed " + completedSteps + " planned steps, but " + failedSteps + " steps failed.";
        }
        log.info("[{}][STATUS_FALLBACK] sessionId={}, result=failure, completedSteps=0, failedSteps={}",
                LOG_STAGE, runContext.sessionId(), failedSteps);
        return "The plan could not be completed.";
    }
}
