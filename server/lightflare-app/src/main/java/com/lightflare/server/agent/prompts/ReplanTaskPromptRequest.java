package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplanTaskPromptRequest {

    private String promptDescription;

    private String task;

    private List<SkillPromptItem> skills;

    private List<MemoryPromptItem> memoryList;

    private List<PlanToolPromptItem> tools;

    private List<LLMPlanResponse.PlanStep> currentPlan;

    private List<String> executionLog;

    private List<String> immutableStepIds;

    private String replanReason;
}
