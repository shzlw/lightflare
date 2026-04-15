package com.lightflare.server.llmproviders.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenAIResponseRequest(
        String model,
        String input,
        @JsonProperty("max_output_tokens")
        Integer maxOutputTokens,
        TextConfig text,
        Reasoning reasoning
) {

    record TextConfig(
            Map<String, Object> format
    ) {
    }

    record Reasoning(
            String effort
    ) {
    }
}
