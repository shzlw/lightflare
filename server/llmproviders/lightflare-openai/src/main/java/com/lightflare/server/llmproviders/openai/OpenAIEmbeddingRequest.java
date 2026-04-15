package com.lightflare.server.llmproviders.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

record OpenAIEmbeddingRequest(
        String model,
        String input,
        int dimensions,
        @JsonProperty("encoding_format")
        String encodingFormat
) {
}
