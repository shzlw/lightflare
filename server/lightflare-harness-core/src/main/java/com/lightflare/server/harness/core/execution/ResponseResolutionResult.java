package com.lightflare.server.harness.core.execution;

public record ResponseResolutionResult(
        String response,
        boolean waitingForUser
) {
}
