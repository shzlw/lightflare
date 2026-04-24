package com.lightflare.server.application;

import lombok.Builder;

@Builder
public record ApplicationTriggerResponse(
        String id,
        String applicationVersionId,
        String triggerType,
        String startStepId,
        String configJson
) {
}
