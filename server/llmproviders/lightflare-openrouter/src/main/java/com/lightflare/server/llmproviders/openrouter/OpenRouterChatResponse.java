package com.lightflare.server.llmproviders.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record OpenRouterChatResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage
) {

    record Choice(
            Integer index,
            Message message,
            @JsonProperty("finish_reason")
            String finishReason
    ) {
    }

    record Message(
            String role,
            String content
    ) {
    }

    record Usage(
            @JsonProperty("prompt_tokens")
            Long promptTokens,
            @JsonProperty("completion_tokens")
            Long completionTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
