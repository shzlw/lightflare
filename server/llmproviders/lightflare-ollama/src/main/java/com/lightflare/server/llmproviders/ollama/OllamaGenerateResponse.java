package com.lightflare.server.llmproviders.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OllamaGenerateResponse(
        String model,
        @JsonProperty("created_at")
        String createdAt,
        String response,
        String thinking,
        Boolean done,
        @JsonProperty("done_reason")
        String doneReason,
        String error,
        @JsonProperty("total_duration")
        Long totalDuration,
        @JsonProperty("load_duration")
        Long loadDuration,
        @JsonProperty("prompt_eval_count")
        Long promptEvalCount,
        @JsonProperty("prompt_eval_duration")
        Long promptEvalDuration,
        @JsonProperty("eval_count")
        Long evalCount,
        @JsonProperty("eval_duration")
        Long evalDuration,
        Object logprobs
) {
}
