package com.lightflare.server.harness.core.execution;

import com.lightflare.server.llmproviders.core.LLMGetResponse;
import java.util.List;

public class PendingUserInputRequest {

    private String stepId;
    private String toolName;
    private List<String> missingInputs = List.of();
    private String question;
    private LLMGetResponse.ToolCall partialToolCall;

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public List<String> getMissingInputs() {
        return missingInputs;
    }

    public void setMissingInputs(List<String> missingInputs) {
        this.missingInputs = missingInputs != null ? List.copyOf(missingInputs) : List.of();
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public LLMGetResponse.ToolCall getPartialToolCall() {
        return partialToolCall;
    }

    public void setPartialToolCall(LLMGetResponse.ToolCall partialToolCall) {
        this.partialToolCall = partialToolCall;
    }
}
