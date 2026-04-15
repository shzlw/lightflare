package com.lightflare.server.chat.events;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.Builder;

@Builder
public record ChatStreamStepStartedEvent(
        String sessionId,
        LLMPlanResponse.PlanStep step
) implements ChatStreamEventPayload {
}
