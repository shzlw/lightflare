package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;

import java.util.List;

record StepExecutionResult(
        String stepId,
        LLMPlanResponse.PlanStep.Status status,
        List<String> executionLogEntries,
        String terminalResponse
) {
}
