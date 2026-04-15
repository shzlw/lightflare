package com.lightflare.server.llmproviders.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record OpenRouterEmbeddingResponse(
        String id,
        String model,
        List<EmbeddingData> data,
        Usage usage
) {

    record EmbeddingData(
            List<Float> embedding,
            String object,
            Integer index
    ) {
    }

    record Usage(
            @JsonProperty("prompt_tokens")
            Long promptTokens,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }
}
