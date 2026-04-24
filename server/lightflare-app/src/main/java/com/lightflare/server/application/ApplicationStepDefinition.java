package com.lightflare.server.application;

import java.util.Map;

public record ApplicationStepDefinition(
        String id,
        String stepKey,
        String name,
        String type,
        String toolName,
        String prompt,
        Map<String, Object> input,
        Map<String, Object> config,
        String onError
) {
    public String resolvedId() {
        return id != null ? id : stepKey;
    }
}
