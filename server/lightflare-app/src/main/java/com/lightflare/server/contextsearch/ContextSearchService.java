package com.lightflare.server.contextsearch;

import com.lightflare.server.agent.memory.MemoryEmbeddingService;
import com.lightflare.server.memory.Memory;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ContextSearchService {

    private final ContextSearchRepository contextSearchRepository;
    private final ContextSearchProperties contextSearchProperties;
    private final MemoryEmbeddingService memoryEmbeddingService;

    public List<ContextSearchResult> search(ContextSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query()) || request.limit() <= 0) {
            return List.of();
        }

        ContextSearchTarget target = request.target() == null
                ? ContextSearchTarget.MEMORY_AND_DOCUMENT
                : request.target();
        int candidateLimit = Math.max(
                contextSearchProperties.getMinCandidateLimit(),
                request.limit() * contextSearchProperties.getCandidateLimitMultiplier()
        );
        Map<String, ScoredResult> scoredResults = new LinkedHashMap<>();

        String queryEmbedding = memoryEmbeddingService.createQueryEmbedding(request.query());
        if (target == ContextSearchTarget.MEMORY_ONLY || target == ContextSearchTarget.MEMORY_AND_DOCUMENT) {
            searchMemories(request, queryEmbedding, candidateLimit, scoredResults);
        }
        if (target == ContextSearchTarget.DOCUMENT_ONLY || target == ContextSearchTarget.MEMORY_AND_DOCUMENT) {
            searchDocumentChunks(request, queryEmbedding, candidateLimit, scoredResults);
        }

        List<ContextSearchResult> rankedResults = scoredResults.values().stream()
                .sorted(Comparator.comparingDouble(ScoredResult::score).reversed()
                        .thenComparing(result -> result.result().createdAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ScoredResult::result)
                .toList();

        return applyDocumentDiversity(rankedResults).stream()
                .limit(request.limit())
                .toList();
    }

    public List<String> searchMemoryIds(ContextSearchRequest request) {
        Set<String> memoryIds = new LinkedHashSet<>();
        for (ContextSearchResult result : search(request)) {
            if (StringUtils.hasText(result.memoryId())) {
                memoryIds.add(result.memoryId());
            }
        }
        return List.copyOf(memoryIds);
    }

    private void searchMemories(ContextSearchRequest request,
                                String queryEmbedding,
                                int candidateLimit,
                                Map<String, ScoredResult> scoredResults) {
        if (StringUtils.hasText(queryEmbedding)) {
            List<MemoryContextSearchRow> vectorRows = contextSearchRepository.findMemoryVectorCandidates(
                    queryEmbedding,
                    request.sessionId(),
                    request.ownerUserId(),
                    request.isAdmin(),
                    candidateLimit
            );
            addMemoryRows(vectorRows, contextSearchProperties.getVectorWeight(), scoredResults);
        }

        List<MemoryContextSearchRow> textRows = contextSearchRepository.findMemoryTextCandidates(
                request.query(),
                request.sessionId(),
                request.ownerUserId(),
                request.isAdmin(),
                candidateLimit
        );
        addMemoryRows(textRows, contextSearchProperties.getTextWeight(), scoredResults);
    }

    private void searchDocumentChunks(ContextSearchRequest request,
                                      String queryEmbedding,
                                      int candidateLimit,
                                      Map<String, ScoredResult> scoredResults) {
        if (StringUtils.hasText(queryEmbedding)) {
            List<DocumentChunkContextSearchRow> vectorRows = contextSearchRepository.findDocumentChunkVectorCandidates(
                    queryEmbedding,
                    request.sessionId(),
                    request.ownerUserId(),
                    request.isAdmin(),
                    candidateLimit
            );
            addDocumentChunkRows(vectorRows, contextSearchProperties.getVectorWeight(), scoredResults);
        }

        List<DocumentChunkContextSearchRow> textRows = contextSearchRepository.findDocumentChunkTextCandidates(
                request.query(),
                request.sessionId(),
                request.ownerUserId(),
                request.isAdmin(),
                candidateLimit
        );
        addDocumentChunkRows(textRows, contextSearchProperties.getTextWeight(), scoredResults);
    }

    private void addMemoryRows(List<MemoryContextSearchRow> rows,
                               double baseWeight,
                               Map<String, ScoredResult> scoredResults) {
        for (int index = 0; index < rows.size(); index++) {
            MemoryContextSearchRow row = rows.get(index);
            String key = ContextSearchResultType.MEMORY + ":" + row.memoryId();
            double score = score(row.scope(), row.createdAt(), index, rows.size(), baseWeight);
            ContextSearchResult result = new ContextSearchResult(
                    ContextSearchResultType.MEMORY,
                    row.memoryId(),
                    null,
                    null,
                    row.title(),
                    row.source(),
                    row.content(),
                    score,
                    row.createdAt()
            );
            mergeResult(scoredResults, key, result, score);
        }
    }

    private void addDocumentChunkRows(List<DocumentChunkContextSearchRow> rows,
                                      double baseWeight,
                                      Map<String, ScoredResult> scoredResults) {
        // No per-document cap here — applyDocumentDiversity() enforces it after final ranking
        // so all candidates can participate in score merging first.
        for (int index = 0; index < rows.size(); index++) {
            DocumentChunkContextSearchRow row = rows.get(index);
            String key = ContextSearchResultType.DOCUMENT_CHUNK + ":" + row.documentChunkId();
            double score = score(row.scope(), row.createdAt(), index, rows.size(), baseWeight);
            ContextSearchResult result = new ContextSearchResult(
                    ContextSearchResultType.DOCUMENT_CHUNK,
                    row.memoryId(),
                    row.documentId(),
                    row.documentChunkId(),
                    row.title(),
                    Memory.SOURCE_IMPORT,
                    row.content(),
                    score,
                    row.createdAt()
            );
            mergeResult(scoredResults, key, result, score);
        }
    }

    private void mergeResult(Map<String, ScoredResult> scoredResults,
                             String key,
                             ContextSearchResult result,
                             double additionalScore) {
        ScoredResult existing = scoredResults.get(key);
        if (existing == null) {
            scoredResults.put(key, new ScoredResult(result, additionalScore));
            return;
        }

        double mergedScore = existing.score() + additionalScore;
        scoredResults.put(key, new ScoredResult(new ContextSearchResult(
                existing.result().type(),
                existing.result().memoryId(),
                existing.result().documentId(),
                existing.result().documentChunkId(),
                existing.result().title(),
                existing.result().source(),
                existing.result().content(),
                mergedScore,
                existing.result().createdAt()
        ), mergedScore));
    }

    private double score(String scope,
                         OffsetDateTime createdAt,
                         int rankIndex,
                         int resultCount,
                         double baseWeight) {
        double rankScore = resultCount <= 1 ? 1.0 : 1.0 - (rankIndex / (double) (resultCount - 1));
        double rankTieBreaker = rankScore * 0.01;
        return baseWeight
                + rankTieBreaker
                + (contextSearchProperties.getRecencyWeight() * recencyScore(createdAt))
                + (contextSearchProperties.getScopeWeight() * scopeBoost(scope));
    }

    private double recencyScore(OffsetDateTime createdAt) {
        if (createdAt == null) {
            return 0.0;
        }
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(createdAt, OffsetDateTime.now()));
        // Exponential decay: ~0.37 at 30 days, ~0.14 at 60 days — avoids the hard cliff
        return Math.exp(-ageDays / 30.0);
    }

    private double scopeBoost(String scope) {
        if (Memory.SCOPE_SESSION.equals(scope)) {
            return 1.0;
        }
        if (Memory.SCOPE_USER.equals(scope)) {
            return 0.6;
        }
        if (Memory.SCOPE_PUBLIC.equals(scope)) {
            return 0.3;
        }
        return 0.0;
    }

    private List<ContextSearchResult> applyDocumentDiversity(List<ContextSearchResult> rankedResults) {
        Map<String, Integer> chunksByDocument = new HashMap<>();
        List<ContextSearchResult> diversifiedResults = new ArrayList<>();
        for (ContextSearchResult result : rankedResults) {
            if (result.type() != ContextSearchResultType.DOCUMENT_CHUNK || !StringUtils.hasText(result.documentId())) {
                diversifiedResults.add(result);
                continue;
            }

            int chunkCount = chunksByDocument.getOrDefault(result.documentId(), 0);
            if (chunkCount >= contextSearchProperties.getMaxChunksPerDocument()) {
                continue;
            }

            chunksByDocument.put(result.documentId(), chunkCount + 1);
            diversifiedResults.add(result);
        }
        return diversifiedResults;
    }

    private record ScoredResult(ContextSearchResult result, double score) {
    }
}
