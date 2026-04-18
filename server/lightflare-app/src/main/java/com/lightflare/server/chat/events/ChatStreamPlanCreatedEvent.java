package com.lightflare.server.chat.events;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import java.util.List;
import lombok.Builder;

@Builder
public record ChatStreamPlanCreatedEvent(
        String executionId,
        String thoughtProcess,
        String selectedSkill,
        List<LLMPlanResponse.PlanStep> steps
) implements ChatStreamEventPayload {
}
