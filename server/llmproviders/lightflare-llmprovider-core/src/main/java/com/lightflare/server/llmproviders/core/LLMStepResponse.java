package com.lightflare.server.llmproviders.core;

import lombok.Data;

import java.util.List;

@Data
public class LLMStepResponse {

    private String thoughtProcess;

    private Action action;

    private LLMGetResponse.ToolCall toolCall;

    private List<String> missingInputs;

    private String response;

    private Boolean stepComplete;

    public enum Action {
        USE_TOOL,
        REQUEST_TOOL_INPUT,
        DIRECT_RESPONSE,
        DESIGN_INSTRUCTIONS
    }
}
