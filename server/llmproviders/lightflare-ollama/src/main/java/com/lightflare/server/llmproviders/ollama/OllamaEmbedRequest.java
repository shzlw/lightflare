package com.lightflare.server.llmproviders.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaEmbedRequest(
        String model,
        String input,
        int dimensions
) {
}
