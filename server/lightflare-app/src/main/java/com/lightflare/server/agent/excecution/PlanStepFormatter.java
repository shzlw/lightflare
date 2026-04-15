package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlanStepFormatter {

    public String formatStepEntry(LLMPlanResponse.PlanStep step, String type, String content) {
        String stepId = step != null && StringUtils.hasText(step.getId()) ? step.getId() : "unknown-step";
        String stepTitle = step != null && StringUtils.hasText(step.getContent()) ? step.getContent() : "untitled";
        return "[" + stepId + "][" + stepTitle + "][" + type + "] " + content;
    }
}
