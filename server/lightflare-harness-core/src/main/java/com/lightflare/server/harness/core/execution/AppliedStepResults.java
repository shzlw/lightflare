package com.lightflare.server.harness.core.execution;

public record AppliedStepResults(
        String userMessage,
        PendingUserInputRequest pendingUserInputRequest
) {
}
