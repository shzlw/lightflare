package com.lightflare.server.agent.excecution;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanContinuationDecision {

    private Outcome outcome;

    private String rationale;

    private String userMessage;

    public enum Outcome {
        CONTINUE,
        REPLAN,
        FINAL_RESPONSE,
        ASK_USER,
        CANNOT_COMPLETE
    }
}
