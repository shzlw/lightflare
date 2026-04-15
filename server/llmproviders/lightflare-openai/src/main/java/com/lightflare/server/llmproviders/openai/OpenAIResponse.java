package com.lightflare.server.llmproviders.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record OpenAIResponse(
        String id,
        String model,
        @JsonProperty("output_text")
        String outputText,
        List<OutputItem> output,
        Usage usage
) {

    record OutputItem(
            String type,
            String id,
            String status,
            List<ContentItem> content
    ) {
    }

    record ContentItem(
            String type,
            String text
    ) {
    }

    record Usage(
            @JsonProperty("input_tokens")
            Long inputTokens,
            @JsonProperty("output_tokens")
            Long outputTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
