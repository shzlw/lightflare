package com.lightflare.server.agent.memory;

import com.lightflare.server.memory.Memory;
import com.lightflare.server.agent.prompts.MemoryPromptItem;

import java.util.List;

public record ConversationContext(Memory currentMemory, List<MemoryPromptItem> promptMemories) {

    public ConversationContext {
        promptMemories = promptMemories == null ? List.of() : List.copyOf(promptMemories);
    }
}
