package com.lightflare.server.agent.memory;

import com.lightflare.server.llmproviders.core.LLMResponse;
import com.lightflare.server.chat.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionUsageService {

    private final ChatSessionRepository chatSessionRepository;

    public void recordLlmUsage(String sessionId, String userId, LLMResponse<?> llmResponse) {
        if (!StringUtils.hasText(sessionId) || llmResponse == null) {
            return;
        }

        if (llmResponse.getTotalTokens() == null
                && llmResponse.getInputTokens() == null
                && llmResponse.getOutputTokens() == null) {
            log.info("Skipping token usage persistence for sessionId={} because usage is absent", sessionId);
            return;
        }

        int updated = chatSessionRepository.recordTokenUsage(
                sessionId,
                userId,
                llmResponse.getTotalTokens(),
                llmResponse.getInputTokens(),
                llmResponse.getOutputTokens()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one chat_session row to be upserted but got " + updated);
        }

        log.info(
                "Recorded token usage for sessionId={}, modelName={}, inputTokens={}, outputTokens={}, totalTokens={}",
                sessionId,
                llmResponse.getModelName(),
                llmResponse.getInputTokens(),
                llmResponse.getOutputTokens(),
                llmResponse.getTotalTokens()
        );
    }
}
