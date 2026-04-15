package com.lightflare.server.llmproviders.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaGenerateRequest(
        String model,
        String prompt,
        String suffix,
        List<String> images,
        Object format,
        String system,
        Boolean stream,
        Boolean think,
        Boolean raw,
        @JsonProperty("keep_alive")
        Object keepAlive,
        Map<String, Object> options,
        Boolean logprobs,
        @JsonProperty("top_logprobs")
        Integer topLogprobs
) {
    public static OllamaGenerateRequest structuredOutput(String model, String prompt, Object format) {
        return new OllamaGenerateRequest(
                model,
                prompt,
                null,
                null,
                format,
                null,
                false,
                false,
                false,
                null,
                null,
                false,
                null
        );
    }
}
