package com.lightflare.server.agent.excecution;

import com.lightflare.server.llmproviders.core.LLMPlanResponse;

import java.util.List;

public interface AgentExecutionListener {

    AgentExecutionListener NOOP = new AgentExecutionListener() {
    };

    default void onPlanCreated(String executionId,
                               String thoughtProcess,
                               String selectedSkill,
                               List<LLMPlanResponse.PlanStep> steps) {
    }

    default void onStepStarted(String executionId, LLMPlanResponse.PlanStep step) {
    }

    default void onStepProgress(String executionId,
                                LLMPlanResponse.PlanStep step,
                                String progressType,
                                String message) {
    }

    default void onStepCompleted(String executionId,
                                 LLMPlanResponse.PlanStep step,
                                 String status,
                                 String terminalResponse,
                                 List<String> executionLogEntries) {
    }

    default void onFinalResponse(String executionId, String response) {
    }
}
