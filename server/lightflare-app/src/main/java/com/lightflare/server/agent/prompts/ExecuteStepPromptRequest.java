package com.lightflare.server.agent.prompts;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import com.lightflare.server.tools.core.ToolDefinition;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecuteStepPromptRequest {

    private String promptDescription;

    private String task;

    private String selectedSkillInstructions;

    private List<MemoryPromptItem> memoryList;

    private List<ToolDefinition> tools;

    private LLMPlanResponse.PlanStep currentStep;

    private StepExecutionStatePrompt stepState;

    private List<String> dependencyContext;

    private List<String> stepExecutionLog;
}
