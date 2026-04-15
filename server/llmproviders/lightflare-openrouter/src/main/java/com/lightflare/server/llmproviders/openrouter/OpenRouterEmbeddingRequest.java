package com.lightflare.server.llmproviders.openrouter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenRouterEmbeddingRequest(
        String model,
        String input,
        Integer dimensions,
        @JsonProperty("encoding_format")
        String encodingFormat,
        @JsonProperty("input_type")
        String inputType,
        Map<String, Object> provider,
        String user
) {
}
