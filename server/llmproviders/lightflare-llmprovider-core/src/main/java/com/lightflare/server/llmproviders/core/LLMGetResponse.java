package com.lightflare.server.llmproviders.core;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class LLMGetResponse {

    private String thoughtProcess;

    private Action action;

    private String selectedSkill;

    private ToolCall toolCall;

    private List<String> missingInputs;

    private String response;

    public enum Action {
        USE_TOOL,
        REQUEST_TOOL_INPUT,
        REQUEST_SKILL_INSTRUCTIONS,
        DESIGN_INSTRUCTIONS,
        DIRECT_RESPONSE
    }

    @Data
    public static class ToolCall {
        private String toolName;
        @JsonAlias("parameters")
        private List<ToolCallArgument> arguments;
    }

    @Data
    public static class ToolCallArgument {
        private String name;
        private List<String> values;
    }
}
