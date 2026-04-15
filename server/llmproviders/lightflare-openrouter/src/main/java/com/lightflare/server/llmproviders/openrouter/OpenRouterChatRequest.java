package com.lightflare.server.llmproviders.openrouter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenRouterChatRequest(
        String model,
        List<Message> messages,
        Boolean stream,
        @JsonProperty("response_format")
        ResponseFormat responseFormat,
        @JsonProperty("max_completion_tokens")
        Integer maxCompletionTokens,
        Double temperature,
        @JsonProperty("top_p")
        Double topP,
        Integer seed,
        String user,
        Map<String, Object> provider
) {

    record Message(
            String role,
            String content
    ) {
    }

    record ResponseFormat(
            String type,
            @JsonProperty("json_schema")
            JsonSchema jsonSchema
    ) {
    }

    record JsonSchema(
            String name,
            Boolean strict,
            JsonNode schema
    ) {
    }
}
