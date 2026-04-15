package com.lightflare.server.llmproviders.openrouter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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
public class OpenRouterService implements LLMProvider {

    private final RestClient restClient;
    private final OpenRouterProperties properties;
    private final Integer defaultMaxOutputTokens;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LLMResponse<LLMGetResponse> getResponse(String input) {
        log.info("Requesting default response from OpenRouter with inputLength={}", input != null ? input.length() : 0);
        return getStructuredResponse(input, LLMGetResponse.class);
    }

    @Override
    public <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType) {
        log.info("-------------- New OpenRouter call --------------");
        log.info("Requesting structured response for responseType={}, inputLength={}",
                responseType != null ? responseType.getSimpleName() : null,
                input != null ? input.length() : 0);

        JsonNode responseSchema = LLMJsonSchemaUtils.generateSchema(responseType);
        OpenRouterChatResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildChatRequest(input, responseType, responseSchema))
                .retrieve()
                .body(OpenRouterChatResponse.class);

        String responseContent = firstChoiceContent(response);
        T outputData;
        try {
            outputData = objectMapper.readValue(responseContent, responseType);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to parse OpenRouter structured response", e);
        }

        OpenRouterChatResponse.Usage usage = response != null ? response.usage() : null;
        return LLMResponse.<T>builder()
                .input(input)
                .outputData(outputData)
                .modelName(response != null && response.model() != null ? response.model() : properties.getModel())
                .totalTokens(usage != null ? usage.totalTokens() : null)
                .inputTokens(usage != null ? usage.promptTokens() : null)
                .outputTokens(usage != null ? usage.completionTokens() : null)
                .responseId(response != null ? response.id() : null)
                .rawResponse(toJson(response))
                .build();
    }

    @Override
    public boolean supportsEmbeddings() {
        return false;
    }

    @Override
    public List<Float> getEmbeddings(String content) {
        log.info("Requesting embeddings from OpenRouter with contentLength={}, embeddingModel={}",
                content != null ? content.length() : 0, properties.getEmbeddingModel());

        OpenRouterEmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OpenRouterEmbeddingRequest(
                        properties.getEmbeddingModel(),
                        content,
                        properties.getEmbeddingDimensions(),
                        properties.getEmbeddingEncodingFormat(),
                        properties.getEmbeddingInputType(),
                        properties.getEmbeddingProvider(),
                        properties.getUser()
                ))
                .retrieve()
                .body(OpenRouterEmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()
                || response.data().getFirst().embedding() == null) {
            throw new IllegalStateException("OpenRouter embedding response did not contain vectors");
        }

        return response.data().getFirst().embedding();
    }

    private <T> OpenRouterChatRequest buildChatRequest(String input, Class<T> responseType, JsonNode responseSchema) {
        return new OpenRouterChatRequest(
                properties.getModel(),
                List.of(new OpenRouterChatRequest.Message("user", buildStructuredPrompt(input, responseSchema))),
                false,
                new OpenRouterChatRequest.ResponseFormat(
                        "json_schema",
                        new OpenRouterChatRequest.JsonSchema(
                                schemaName(responseType),
                                true,
                                responseSchema
                        )
                ),
                properties.getMaxCompletionTokens() != null ? properties.getMaxCompletionTokens() : defaultMaxOutputTokens,
                properties.getTemperature(),
                properties.getTopP(),
                properties.getSeed(),
                properties.getUser(),
                properties.getProvider()
        );
    }

    private String buildStructuredPrompt(String input, JsonNode responseSchema) {
        return """
                Return only valid JSON with no markdown fences and no extra text.
                The JSON must match this JSON Schema exactly:
                %s

                User input:
                %s
                """.formatted(
                responseSchema.toPrettyString(),
                input == null ? "" : input
        );
    }

    private String firstChoiceContent(OpenRouterChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new IllegalStateException("OpenRouter chat response did not contain output text");
        }
        return response.choices().getFirst().message().content();
    }

    private String schemaName(Class<?> responseType) {
        return responseType.getSimpleName()
                .replaceAll("[^A-Za-z0-9_-]", "_")
                .toLowerCase();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }
}
