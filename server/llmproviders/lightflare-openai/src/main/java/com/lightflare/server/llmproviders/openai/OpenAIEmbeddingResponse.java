package com.lightflare.server.llmproviders.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record OpenAIEmbeddingResponse(
        List<EmbeddingData> data,
        String model,
        String object,
        Usage usage
) {

    record EmbeddingData(
            List<Float> embedding,
            Integer index,
            String object
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
