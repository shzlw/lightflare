package com.lightflare.server.llmproviders.openai;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMJsonSchemaUtils;
import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.llmproviders.core.LLMResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class OpenAIService implements LLMProvider {

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

    private final RestClient restClient;
    private final String modelName;
    private final String embeddingModelName;
    private final int embeddingDimensions;
    private final String reasoningEffort;
    private final Integer maxOutputTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LLMResponse<LLMGetResponse> getResponse(String input) {
        log.info("Requesting default structured response with inputLength={}", input != null ? input.length() : 0);
        return getStructuredResponse(input, LLMGetResponse.class);
    }

    @Override
    public <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType) {
        log.info("-------------- New OpenAI Responses call --------------");
        log.info("Requesting structured response for responseType={}, inputLength={}",
                responseType != null ? responseType.getSimpleName() : null,
                input != null ? input.length() : 0);

        OpenAIResponse response = restClient.post()
                .uri("/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildResponseRequest(input, responseType))
                .retrieve()
                .body(OpenAIResponse.class);

        String responseContent = firstOutputText(response);
        T outputData;
        try {
            outputData = objectMapper.readValue(responseContent, responseType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse OpenAI structured response", e);
        }

        OpenAIResponse.Usage usage = response != null ? response.usage() : null;
        Long inputTokens = usage != null ? usage.inputTokens() : null;
        Long outputTokens = usage != null ? usage.outputTokens() : null;
        Long totalTokens = usage != null ? usage.totalTokens() : null;

        log.info("Received structured response for responseType={}, responseId={}",
                responseType != null ? responseType.getSimpleName() : null,
                response != null ? response.id() : null);
        if (usage != null) {
            log.info("usage: input token: {}, output token: {}, total: {}",
                    inputTokens, outputTokens, totalTokens);
        }

        return LLMResponse.<T>builder()
                .input(input)
                .outputData(outputData)
                .modelName(response != null && response.model() != null ? response.model() : modelName)
                .totalTokens(totalTokens)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .responseId(response != null ? response.id() : null)
                .rawResponse(toJson(response))
                .build();
    }

    @Override
    public boolean supportsEmbeddings() {
        return true;
    }

    @Override
    public List<Float> getEmbeddings(String content) {
        log.info("Requesting OpenAI embedding for contentLength={}", content != null ? content.length() : 0);

        OpenAIEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildEmbeddingRequest(content))
                .retrieve()
                .body(OpenAIEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().getFirst().embedding() == null) {
            throw new IllegalStateException("OpenAI embedding response did not contain embedding data");
        }

        List<Float> embeddingVector = response.data().getFirst().embedding();
        log.info("Embedding length: {}", embeddingVector.size());
        return embeddingVector;
    }

    private <T> OpenAIResponseRequest buildResponseRequest(String input, Class<T> responseType) {
        return new OpenAIResponseRequest(
                modelName,
                input == null ? "" : input,
                effectiveMaxOutputTokens(),
                new OpenAIResponseRequest.TextConfig(LLMJsonSchemaUtils.structuredResponseFormat(responseType)),
                hasText(reasoningEffort) ? new OpenAIResponseRequest.Reasoning(reasoningEffort) : null
        );
    }

    private OpenAIEmbeddingRequest buildEmbeddingRequest(String content) {
        return new OpenAIEmbeddingRequest(
                embeddingModelName,
                content == null ? "" : content,
                embeddingDimensions,
                "float"
        );
    }

    private String firstOutputText(OpenAIResponse response) {
        if (response == null) {
            throw new IllegalStateException("OpenAI response was empty");
        }
        if (hasText(response.outputText())) {
            return response.outputText();
        }

        if (response.output() == null) {
            throw new IllegalStateException("OpenAI response did not contain output");
        }
        for (OpenAIResponse.OutputItem outputItem : response.output()) {
            if (outputItem.content() == null) {
                continue;
            }
            for (OpenAIResponse.ContentItem contentItem : outputItem.content()) {
                if (hasText(contentItem.text())) {
                    return contentItem.text();
                }
            }
        }

        throw new IllegalStateException("OpenAI response did not contain output text");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int effectiveMaxOutputTokens() {
        return maxOutputTokens != null ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }
}
