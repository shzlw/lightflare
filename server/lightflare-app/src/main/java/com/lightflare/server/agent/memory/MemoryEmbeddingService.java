package com.lightflare.server.agent.memory;

import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.memory.DocumentChunkRepository;
import com.lightflare.server.memory.EmbeddingVector;
import com.lightflare.server.memory.MemoryRepository;
import com.lightflare.server.utils.DateUtils;
import com.lightflare.server.utils.LuceneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEmbeddingService {

    private final LLMProvider llmProvider;
    private final MemoryRepository memoryRepository;
    private final DocumentChunkRepository documentChunkRepository;

    @Async("memoryEmbeddingExecutor")
    public void generateEmbeddingAsync(String memoryId, String content) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(content)) {
            return;
        }

        try {
            String embedding = createQueryEmbedding(content);
            if (!StringUtils.hasText(embedding)) {
                log.info("Skipping async embedding generation for memoryId={} because embedding is empty", memoryId);
                return;
            }

            memoryRepository.updateEmbeddingById(memoryId, embedding, DateUtils.now());
            log.info("Generated embedding asynchronously for memoryId={}", memoryId);
        } catch (RuntimeException e) {
            log.warn("Failed to generate embedding asynchronously for memoryId={}", memoryId, e);
        }
    }

    public void generateEmbedding(String memoryId, String content) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(content)) {
            return;
        }

        String embedding = createQueryEmbedding(content);
        if (!StringUtils.hasText(embedding)) {
            log.info("Skipping embedding generation for memoryId={} because embedding is empty", memoryId);
            return;
        }

        memoryRepository.updateEmbeddingById(memoryId, embedding, DateUtils.now());
        log.info("Generated embedding for memoryId={}", memoryId);
    }

    public void generateDocumentChunkEmbedding(String documentChunkId, String content) {
        if (!StringUtils.hasText(documentChunkId) || !StringUtils.hasText(content)) {
            return;
        }

        String embedding = createQueryEmbedding(content);
        if (!StringUtils.hasText(embedding)) {
            log.info("Skipping embedding generation for documentChunkId={} because embedding is empty", documentChunkId);
            return;
        }

        documentChunkRepository.updateEmbeddingById(documentChunkId, embedding, DateUtils.now());
        log.info("Generated embedding for documentChunkId={}", documentChunkId);
    }

    public String createQueryEmbedding(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        if (!llmProvider.supportsEmbeddings()) {
            log.info("Skipping query embedding generation because provider {} does not support embeddings",
                    llmProvider.getClass().getSimpleName());
            return null;
        }

        String normalizedContent = LuceneNormalizer.normalize(content, false);
        List<Float> embeddings = llmProvider.getEmbeddings(normalizedContent);
        if (embeddings == null || embeddings.isEmpty()) {
            log.info("Skipping query embedding generation because provider {} returned no embedding values",
                    llmProvider.getClass().getSimpleName());
            return null;
        }
        return EmbeddingVector.fromList(embeddings).value();
    }
}
