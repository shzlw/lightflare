package com.lightflare.server.chat.events;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.Builder;

@Builder
public record ChatStreamStepProgressEvent(
        String sessionId,
        LLMPlanResponse.PlanStep step,
        String progressType,
        String message
) implements ChatStreamEventPayload {
}
