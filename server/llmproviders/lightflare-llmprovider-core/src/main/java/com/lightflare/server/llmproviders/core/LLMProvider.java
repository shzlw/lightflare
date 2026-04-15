package com.lightflare.server.llmproviders.core;

import java.util.List;

public interface LLMProvider {

    LLMResponse<LLMGetResponse> getResponse(String input);

    <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType);

    default boolean supportsEmbeddings() {
        return false;
    }

    default List<Float> getEmbeddings(String content) {
        throw new UnsupportedOperationException("Embeddings are not supported by this LLM provider");
    }
}
