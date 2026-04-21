package com.lightflare.server.agent.memory;

import com.lightflare.server.agent.usage.AgentUsageRecorder;
import com.lightflare.server.harness.core.run.HarnessRunContext;
import com.lightflare.server.llmproviders.core.LLMResponse;
import com.lightflare.server.chat.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionUsageService implements AgentUsageRecorder {

    private final ChatSessionRepository chatSessionRepository;

    @Override
    public void recordLlmUsage(String executionId, String executionType, String userId, LLMResponse<?> llmResponse) {
        if (!StringUtils.hasText(executionId) || llmResponse == null) {
            return;
        }

        if (!HarnessRunContext.EXECUTION_TYPE_CHAT.equals(executionType)) {
            log.info("Skipping chat token usage persistence for executionId={}, executionType={}",
                    executionId,
                    executionType);
            return;
        }

        if (llmResponse.getTotalTokens() == null
                && llmResponse.getInputTokens() == null
                && llmResponse.getOutputTokens() == null) {
            log.info("Skipping token usage persistence for executionId={} because usage is absent", executionId);
            return;
        }

        int updated = chatSessionRepository.recordTokenUsage(
                executionId,
                userId,
                llmResponse.getTotalTokens(),
                llmResponse.getInputTokens(),
                llmResponse.getOutputTokens()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one chat_session row to be upserted but got " + updated);
        }

        log.info(
                "Recorded token usage for executionId={}, modelName={}, inputTokens={}, outputTokens={}, totalTokens={}",
                executionId,
                llmResponse.getModelName(),
                llmResponse.getInputTokens(),
                llmResponse.getOutputTokens(),
                llmResponse.getTotalTokens()
        );
    }
}
