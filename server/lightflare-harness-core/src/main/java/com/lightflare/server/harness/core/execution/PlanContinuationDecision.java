package com.lightflare.server.harness.core.execution;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanContinuationDecision {

    private Outcome outcome;

    private String rationale;

    private String userMessage;

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public enum Outcome {
        CONTINUE,
        REPLAN,
        FINAL_RESPONSE,
        ASK_USER,
        CANNOT_COMPLETE
    }
}
