package com.lightflare.server.agent.excecution;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseResolution {

    private Outcome outcome;

    private String feedback;

    private String userMessage;

    public enum Outcome {
        ACCEPT,
        REFINE_RESPONSE,
        ASK_FOR_MORE_INFO,
        CANNOT_COMPLETE
    }
}
