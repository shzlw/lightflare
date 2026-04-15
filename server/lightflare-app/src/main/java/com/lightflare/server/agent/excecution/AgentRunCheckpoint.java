package com.lightflare.server.agent.excecution;

import com.lightflare.server.agent.prompts.MemoryPromptItem;
import com.lightflare.server.llmproviders.core.LLMPlanResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AgentRunCheckpoint {

    private int schemaVersion = 1;
    private String task;
    private String sessionId;
    private String userId;
    private List<MemoryPromptItem> promptMemories = new ArrayList<>();
    private String selectedSkillName;
    private String selectedSkillInstructions;
    private List<LLMPlanResponse.PlanStep> steps = new ArrayList<>();
    private List<String> executionLog = new ArrayList<>();
    private int waveNumber;
    private int replanCount;
    private String finalResponse;
    private String error;

    public List<MemoryPromptItem> safePromptMemories() {
        return promptMemories != null ? promptMemories : List.of();
    }

    public List<LLMPlanResponse.PlanStep> safeSteps() {
        return steps != null ? steps : List.of();
    }

    public List<String> safeExecutionLog() {
        return executionLog != null ? executionLog : List.of();
    }
}
