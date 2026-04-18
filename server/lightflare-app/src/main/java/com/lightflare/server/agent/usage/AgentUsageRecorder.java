package com.lightflare.server.agent.usage;

import com.lightflare.server.llmproviders.core.LLMResponse;

public interface AgentUsageRecorder {

    void recordLlmUsage(String executionId, String executionType, String userId, LLMResponse<?> llmResponse);
}
