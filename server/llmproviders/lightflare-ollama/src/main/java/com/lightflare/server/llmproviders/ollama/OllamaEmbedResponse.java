package com.lightflare.server.llmproviders.ollama;

import java.util.List;

public record OllamaEmbedResponse(
        List<List<Float>> embeddings
) {
}
