package com.lightflare.server.agent.memory;

import com.lightflare.server.agent.AgentRunContext;
import com.lightflare.server.contextsearch.ContextSearchRequest;
import com.lightflare.server.contextsearch.ContextSearchResult;
import com.lightflare.server.contextsearch.ContextSearchResultType;
import com.lightflare.server.contextsearch.ContextSearchService;
import com.lightflare.server.contextsearch.ContextSearchTarget;
import com.lightflare.server.config.MemoryProperties;
import com.lightflare.server.chat.ChatMessage;
import com.lightflare.server.chat.ChatSession;
import com.lightflare.server.memory.Memory;
import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.llmproviders.core.LLMResponse;
import com.lightflare.server.agent.prompts.CompactMemoryPrompt;
import com.lightflare.server.agent.prompts.MemoryPromptItem;
import com.lightflare.server.chat.ChatMessageRepository;
import com.lightflare.server.chat.ChatSessionRepository;
import com.lightflare.server.memory.MemoryRepository;
import com.lightflare.server.utils.DateUtils;
import com.lightflare.server.utils.FileUtils;
import com.lightflare.server.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private static final String CHAT_MESSAGE_SOURCE_USER = "user";
    private static final String CHAT_MESSAGE_SOURCE_LLM = "llm";
    private static final long COMPACTION_COOLDOWN_SECONDS = 300; // 5 minutes
    private static final int SEARCH_QUERY_CONTEXT_MESSAGES = 3;

    private final Map<String, Instant> lastCompactionAttempt = new ConcurrentHashMap<>();

    private final MemoryProperties memoryProperties;
    private final LLMProvider llmProvider;
    private final ChatSessionUsageService chatSessionUsageService;
    private final MemoryRepository memoryRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final ContextSearchService contextSearchService;

    /**
     * Prepares the full conversation context for the agent.
     *
     * <p><strong>Async embedding note:</strong> when a memory is persisted via
     * {@code insertMemory()}, its embedding is generated asynchronously.  If the user
     * sends two rapid messages, the first message's embedding may not yet be written
     * when the second message's context search runs.  This is acceptable because all
     * session memories are loaded directly via {@code loadSessionMemories()} (which
     * does <em>not</em> require embeddings), so no conversation turns are lost.
     * The only effect is that the first message won't appear as a <em>context-search</em>
     * hit for the second message, which is a minor redundancy because it is already in
     * the session history list.</p>
     */
    public ConversationContext prepare(AgentRunContext runContext) {
        log.info("Preparing conversation context for sessionId={}, userId={}",
                runContext.sessionId(), runContext.userId());
        persistChatMessage(runContext);
        Memory currentMemory = persistChatMessageAsMemory(runContext);

        List<Memory> sessionMemories = loadSessionMemories(currentMemory);
        log.info("Loaded {} session memories for sessionId={}", sessionMemories.size(), runContext.sessionId());
        if (shouldCompactMemory(runContext.sessionId())) {
            log.info("Memory compaction triggered for sessionId={}", runContext.sessionId());
            sessionMemories = compactMemory(runContext, currentMemory, sessionMemories);
        }

        List<ContextSearchResult> contextSearchResults = findRelevantContext(currentMemory, sessionMemories);
        List<MemoryPromptItem> promptMemories = new ArrayList<>(sessionMemories.stream()
                .map(this::toSessionMemoryPromptItem)
                .toList());
        promptMemories.addAll(contextSearchResults.stream()
                .map(this::toContextSearchPromptItem)
                .toList());
        log.info("Prepared conversation context for sessionId={} with {} prompt memories ({} context search results)",
                runContext.sessionId(), promptMemories.size(), contextSearchResults.size());

        return new ConversationContext(currentMemory, promptMemories);
    }

    public void persistAssistantResponse(AgentRunContext runContext, String response) {
        if (!StringUtils.hasText(response)) {
            log.info("Skipping assistant response persistence for sessionId={} because response is empty",
                    runContext.sessionId());
            return;
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSessionId(runContext.sessionId());
        chatMessage.setSource(CHAT_MESSAGE_SOURCE_LLM);
        chatMessage.setContent(response);
        insertChatMessage(chatMessage);

        Memory memory = new Memory();
        memory.setId(UUID.randomUUID().toString());
        memory.setOwnerUserId(runContext.userId());
        memory.setSessionId(runContext.sessionId());
        memory.setScope(Memory.SCOPE_SESSION);
        memory.setKind(Memory.KIND_CHAT_MESSAGE);
        memory.setRetentionPolicy(Memory.RETENTION_POLICY_COMPACTABLE);
        memory.setSource(Memory.SOURCE_AGENT);
        memory.setStatus(Memory.STATUS_ACTIVE);
        memory.setContent(response);
        insertMemory(memory);
        log.info("Persisted assistant response for sessionId={}, responseLength={}",
                runContext.sessionId(), response.length());
    }

    private void persistChatMessage(AgentRunContext runContext) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSessionId(runContext.sessionId());
        chatMessage.setSource(CHAT_MESSAGE_SOURCE_USER);
        chatMessage.setContent(runContext.task());
        insertChatMessage(chatMessage);
        log.info("Persisted user chat message for sessionId={}, taskLength={}",
                runContext.sessionId(), runContext.task() != null ? runContext.task().length() : 0);
    }

    private Memory persistChatMessageAsMemory(AgentRunContext runContext) {
        Memory memory = new Memory();
        memory.setId(UUID.randomUUID().toString());
        memory.setOwnerUserId(runContext.userId());
        memory.setSessionId(runContext.sessionId());
        memory.setScope(Memory.SCOPE_SESSION);
        memory.setKind(Memory.KIND_CHAT_MESSAGE);
        memory.setRetentionPolicy(Memory.RETENTION_POLICY_COMPACTABLE);
        memory.setSource(Memory.SOURCE_USER);
        memory.setStatus(Memory.STATUS_ACTIVE);
        memory.setContent(runContext.task());
        insertMemory(memory);
        log.info("Persisted user memory entry id={} for sessionId={}", memory.getId(), runContext.sessionId());
        return memory;
    }

    private List<Memory> loadSessionMemories(Memory currentMemory) {
        return memoryRepository.findActiveBySessionIdAndOwnerUserId(
                currentMemory.getSessionId(),
                currentMemory.getOwnerUserId(),
                memoryProperties.getSessionMemoryLimit()
        );
    }

    private boolean shouldCompactMemory(String sessionId) {
        Instant lastAttempt = lastCompactionAttempt.get(sessionId);
        if (lastAttempt != null && Instant.now().isBefore(lastAttempt.plusSeconds(COMPACTION_COOLDOWN_SECONDS))) {
            log.info("Skipping memory compaction for sessionId={} because cooldown is active", sessionId);
            return false;
        }
        return chatSessionRepository.findById(sessionId)
                .map(ChatSession::getTotalTokens)
                .filter(totalTokens -> totalTokens > memoryProperties.getCompactionTokenThreshold())
                .isPresent();
    }

    private List<Memory> compactMemory(AgentRunContext runContext, Memory currentMemory, List<Memory> memoryList) {
        if (CollectionUtils.isEmpty(memoryList)) {
            log.info("Skipping memory compaction for sessionId={} because memory list is empty", runContext.sessionId());
            return memoryList;
        }

        List<Memory> compactableMemories = memoryList.stream()
                .filter(memory -> Memory.RETENTION_POLICY_COMPACTABLE.equals(memory.getRetentionPolicy()))
                .toList();
        if (compactableMemories.isEmpty()) {
            log.info("Skipping memory compaction for sessionId={} because there are no compactable active memories",
                    runContext.sessionId());
            return memoryList;
        }

        List<Memory> memoryToCompactList = new ArrayList<>(
                compactableMemories.subList(0, Math.min(memoryProperties.getCompactionBatchSize(), compactableMemories.size()))
        );
        log.info("Compacting {} memories for sessionId={}", memoryToCompactList.size(), runContext.sessionId());

        CompactMemoryPrompt compactMemoryPrompt = new CompactMemoryPrompt();
        compactMemoryPrompt.setPromptDescription(FileUtils.loadPromptTemplate("compact-memory.md"));
        compactMemoryPrompt.setInputsToCompact(memoryToCompactList.stream()
                .map(this::toSessionMemoryPromptItem)
                .toList());

        String compactMemoryPromptJson = JsonUtils.toJson(compactMemoryPrompt);
        lastCompactionAttempt.put(runContext.sessionId(), Instant.now());
        LLMResponse<LLMGetResponse> compactMemoryResponse = llmProvider.getResponse(compactMemoryPromptJson);
        chatSessionUsageService.recordLlmUsage(runContext.sessionId(), runContext.userId(), compactMemoryResponse);
        String compactedContent = compactMemoryResponse.getOutputData() != null
                ? compactMemoryResponse.getOutputData().getResponse()
                : null;
        if (!StringUtils.hasText(compactedContent)) {
            log.warn("Skipping memory compaction persistence for sessionId={} because compacted content is empty",
                    runContext.sessionId());
            return memoryList;
        }

        Memory compactedMemory = new Memory();
        compactedMemory.setId(UUID.randomUUID().toString());
        compactedMemory.setOwnerUserId(runContext.userId());
        compactedMemory.setSessionId(runContext.sessionId());
        compactedMemory.setScope(Memory.SCOPE_SESSION);
        compactedMemory.setKind(Memory.KIND_SUMMARY);
        compactedMemory.setRetentionPolicy(Memory.RETENTION_POLICY_PRESERVE_RAW);
        compactedMemory.setSource(Memory.SOURCE_SYSTEM);
        compactedMemory.setStatus(Memory.STATUS_ACTIVE);
        compactedMemory.setContent(compactedContent);
        insertMemory(compactedMemory);
        log.info("Persisted compacted memory id={} for sessionId={}", compactedMemory.getId(), runContext.sessionId());

        List<String> toDeleteIds = memoryToCompactList.stream()
                .map(Memory::getId)
                .toList();
        OffsetDateTime now = DateUtils.now();
        int archivedCount = memoryRepository.updateStatusByMemoryIds(
                toDeleteIds,
                Memory.STATUS_ARCHIVED,
                Memory.STATUS_REASON_COMPACTED,
                now,
                Memory.SOURCE_SYSTEM,
                now
        );
        log.info("Archived {} compacted memories for sessionId={}", archivedCount, runContext.sessionId());

        return loadSessionMemories(currentMemory);
    }

    private List<ContextSearchResult> findRelevantContext(Memory currentMemory, List<Memory> sessionMemories) {
        Set<String> memoryIds = new HashSet<>();
        memoryIds.add(currentMemory.getId());
        for (Memory sessionMemory : sessionMemories) {
            if (StringUtils.hasText(sessionMemory.getId())) {
                memoryIds.add(sessionMemory.getId());
            }
        }

        String searchQuery = buildSearchQuery(currentMemory, sessionMemories);
        List<ContextSearchResult> results = contextSearchService.search(new ContextSearchRequest(
                searchQuery,
                currentMemory.getSessionId(),
                currentMemory.getOwnerUserId(),
                false,
                ContextSearchTarget.MEMORY_AND_DOCUMENT,
                memoryProperties.getSimilarityResultLimit()
        ));
        return results.stream()
                .filter(result -> !memoryIds.contains(result.memoryId()))
                .toList();
    }

    /**
     * Builds a richer search query by prepending recent session messages to the
     * current user message. This gives the vector/text search more conversation
     * context so follow-up messages like "no, I meant the other one" can still
     * retrieve relevant memories.
     */
    private String buildSearchQuery(Memory currentMemory, List<Memory> sessionMemories) {
        String currentContent = currentMemory.getContent();
        if (CollectionUtils.isEmpty(sessionMemories) || sessionMemories.size() <= 1) {
            return currentContent;
        }

        // Take the last few messages (excluding the current one) as context
        int startIndex = Math.max(0, sessionMemories.size() - 1 - SEARCH_QUERY_CONTEXT_MESSAGES);
        StringBuilder queryBuilder = new StringBuilder();
        for (int i = startIndex; i < sessionMemories.size() - 1; i++) {
            Memory mem = sessionMemories.get(i);
            if (mem != null && StringUtils.hasText(mem.getContent())) {
                queryBuilder.append(mem.getContent()).append("\n");
            }
        }
        queryBuilder.append(currentContent);
        return queryBuilder.toString();
    }

    private MemoryPromptItem toSessionMemoryPromptItem(Memory memory) {
        MemoryPromptItem promptItem = new MemoryPromptItem();
        promptItem.setType(MemoryPromptItem.TYPE_SESSION_HISTORY);
        promptItem.setSource(memory.getSource());
        promptItem.setContent(memory.getContent());
        promptItem.setCreatedAt(memory.getCreatedAt());
        return promptItem;
    }

    private MemoryPromptItem toContextSearchPromptItem(ContextSearchResult contextSearchResult) {
        MemoryPromptItem promptItem = new MemoryPromptItem();
        if (contextSearchResult.type() == ContextSearchResultType.DOCUMENT_CHUNK) {
            promptItem.setType(MemoryPromptItem.TYPE_RETRIEVED_DOCUMENT);
        } else {
            promptItem.setType(MemoryPromptItem.TYPE_RETRIEVED_MEMORY);
        }
        promptItem.setSource(contextSearchResult.source());
        promptItem.setContent(contextSearchResult.content());
        promptItem.setCreatedAt(contextSearchResult.createdAt());
        return promptItem;
    }

    private void insertChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getCreatedAt() == null) {
            chatMessage.setCreatedAt(DateUtils.now());
        }
        int inserted = chatMessageRepository.insert(
                chatMessage.getId(),
                chatMessage.getSessionId(),
                chatMessage.getSource(),
                chatMessage.getContent(),
                chatMessage.getCreatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one chat_message row to be inserted but got " + inserted);
        }
    }

    private void insertMemory(Memory memory) {
        if (!StringUtils.hasText(memory.getContent())) {
            throw new IllegalArgumentException("Memory content must not be empty");
        }
        int inserted = memoryRepository.insert(
                memory.getId(),
                memory.getOwnerUserId(),
                memory.getSessionId(),
                memory.getScope(),
                memory.getKind(),
                memory.getRetentionPolicy(),
                memory.getSource(),
                memory.getStatus(),
                memory.getStatusReason(),
                memory.getStatusChangedAt(),
                memory.getStatusChangedBy(),
                memory.getContent(),
                memory.getEmbeddingVector()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one memory row to be inserted but got " + inserted);
        }
        memoryEmbeddingService.generateEmbeddingAsync(memory.getId(), memory.getContent());
    }
}
