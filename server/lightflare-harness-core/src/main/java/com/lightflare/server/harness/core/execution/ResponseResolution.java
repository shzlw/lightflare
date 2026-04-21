package com.lightflare.server.harness.core.execution;

public class ResponseResolution {

    private Outcome outcome;
    private String feedback;
    private String userMessage;

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public enum Outcome {
        ACCEPT,
        REFINE_RESPONSE,
        ASK_FOR_MORE_INFO,
        CANNOT_COMPLETE
    }
}
