package com.lightflare.server.chat.events;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import java.util.List;
import lombok.Builder;

@Builder
public record ChatStreamStepCompletedEvent(
        String sessionId,
        LLMPlanResponse.PlanStep step,
        String status,
        String terminalResponse,
        List<String> executionLogEntries
) implements ChatStreamEventPayload {
}
