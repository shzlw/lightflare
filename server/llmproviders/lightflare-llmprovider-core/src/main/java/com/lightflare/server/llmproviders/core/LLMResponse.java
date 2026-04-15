package com.lightflare.server.llmproviders.core;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LLMResponse<T> {

    String input;
    T outputData;
    String modelName;
    Long totalTokens;
    Long inputTokens;
    Long outputTokens;
    String responseId;
    String rawResponse;
}
