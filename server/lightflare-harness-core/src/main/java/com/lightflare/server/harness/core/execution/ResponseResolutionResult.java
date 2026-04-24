package com.lightflare.server.harness.core.execution;

import java.util.List;

public record ResponseResolutionResult(
        String response,
        boolean waitingForUser,
        List<GeneratedArtifact> artifacts
) {
    public ResponseResolutionResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public ResponseResolutionResult(String response, boolean waitingForUser) {
        this(response, waitingForUser, List.of());
    }
}
