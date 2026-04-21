package com.lightflare.server.harness.core.execution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;
public class PlanStepFormatter {

    public String formatStepEntry(LLMPlanResponse.PlanStep step, String type, String content) {
        String stepId = step != null && !isBlank(step.getId()) ? step.getId() : "unknown-step";
        String stepTitle = step != null && !isBlank(step.getContent()) ? step.getContent() : "untitled";
        return "[" + stepId + "][" + stepTitle + "][" + type + "] " + content;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
