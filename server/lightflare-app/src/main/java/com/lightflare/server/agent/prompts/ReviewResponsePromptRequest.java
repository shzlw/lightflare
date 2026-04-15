package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponsePromptRequest {

    private String promptDescription;

    private String task;

    private List<LLMPlanResponse.PlanStep> plan;

    private List<String> executionLog;

    private String candidateResponse;
}
