package com.lightflare.server.workflow;

import java.util.Map;

public record WorkflowStepDefinition(
        String id,
        String stepId,
        String name,
        String type,
        String toolName,
        String prompt,
        Map<String, Object> input,
        Map<String, Object> output,
        String onError
) {
    public String resolvedId() {
        return id != null ? id : stepId;
    }
}
