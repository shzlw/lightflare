package com.lightflare.server.agent.excecution;

record ResponseResolutionResult(
        String response,
        boolean waitingForUser
) {
}
