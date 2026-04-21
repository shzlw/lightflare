package com.lightflare.server.harness.core.execution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;

import java.util.List;

public record StepExecutionResult(
        String stepId,
        LLMPlanResponse.PlanStep.Status status,
        List<String> executionLogEntries,
        String userMessage,
        PendingUserInputRequest pendingUserInputRequest
) {
}
