package com.lightflare.server.llmproviders.ollama;

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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class OllamaService implements LLMProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String modelName;
    private final String embeddingModel;
    private final int embeddingDimensions;

    @Override
    public LLMResponse<LLMGetResponse> getResponse(String input) {
        log.info("Requesting default response from Ollama with inputLength={}", input != null ? input.length() : 0);
        return getStructuredResponse(input, LLMGetResponse.class);
    }

    @Override
    public <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType) {
        log.info("-------------- New Ollama call --------------");
        log.info("Requesting structured response for responseType={}, inputLength={}",
                responseType != null ? responseType.getSimpleName() : null,
                input != null ? input.length() : 0);
        JsonNode responseSchema = LLMJsonSchemaUtils.structuredOutputSchema(responseType);

        OllamaGenerateResponse response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(OllamaGenerateRequest.structuredOutput(
                        modelName,
                        buildStructuredPrompt(input, responseType, responseSchema),
                        responseSchema
                ))
                .retrieve()
                .body(OllamaGenerateResponse.class);

        if (response == null) {
            throw new IllegalStateException("Ollama response did not contain output text");
        }
        if (!StringUtils.hasText(response.response())) {
            log.error("Ollama returned blank structured response for responseType={}, model={}, done={}, doneReason={}, error={}, thinking={}, promptEvalCount={}, evalCount={}",
                    responseType != null ? responseType.getSimpleName() : null,
                    response.model(),
                    response.done(),
                    response.doneReason(),
                    response.error(),
                    response.thinking(),
                    response.promptEvalCount(),
                    response.evalCount());
            throw new IllegalStateException("Ollama response did not contain output text");
        }

        T outputData;
        try {
            outputData = objectMapper.readValue(response.response(), responseType);
        } catch (JacksonException e) {
            log.error("Failed to parse Ollama structured response for responseType={}, rawResponse={}",
                    responseType != null ? responseType.getSimpleName() : null,
                    response.response(),
                    e);
            throw new IllegalStateException("Failed to parse Ollama structured response", e);
        }

        Long inputTokens = response.promptEvalCount();
        Long outputTokens = response.evalCount();
        Long totalTokens = inputTokens != null && outputTokens != null ? inputTokens + outputTokens : null;

        return LLMResponse.<T>builder()
                .input(input)
                .outputData(outputData)
                .modelName(modelName)
                .totalTokens(totalTokens)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .responseId(null)
                .rawResponse(response.response())
                .build();
    }

    @Override
    public boolean supportsEmbeddings() {
        return true;
    }

    @Override
    public List<Float> getEmbeddings(String content) {
        String effectiveEmbeddingModel = embeddingModel != null && !embeddingModel.isBlank() ? embeddingModel : modelName;
        log.info("Requesting embeddings from Ollama with contentLength={}, embeddingModel={}",
                content != null ? content.length() : 0, effectiveEmbeddingModel);

        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OllamaEmbedRequest(effectiveEmbeddingModel, content, embeddingDimensions))
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama embedding response did not contain vectors");
        }

        return response.embeddings().getFirst();
    }

    private <T> String buildStructuredPrompt(String input, Class<T> responseType, JsonNode responseSchema) {
        return """
                You are a JSON API.
                Return only valid JSON with no markdown fences and no extra text.
                The JSON must match this JSON Schema exactly.
                Class: %s
                Fields:
                %s
                JSON Schema:
                %s

                User input:
                %s
                """.formatted(
                responseType.getSimpleName(),
                describeFields(responseType),
                responseSchema.toPrettyString(),
                input == null ? "" : input
        );
    }

    private String describeFields(Class<?> type) {
        StringBuilder description = new StringBuilder();
        for (Field field : type.getDeclaredFields()) {
            description.append("- ")
                    .append(field.getName())
                    .append(": ")
                    .append(field.getType().getSimpleName())
                    .append('\n');
        }
        return description.toString().trim();
    }

}
