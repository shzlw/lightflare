package com.lightflare.server.contextsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightflare.server.agent.memory.MemoryEmbeddingService;
import com.lightflare.server.llmproviders.core.LLMGetResponse;
import com.lightflare.server.llmproviders.core.LLMProvider;
import com.lightflare.server.llmproviders.core.LLMResponse;
import com.lightflare.server.memory.Memory;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextSearchServiceTest {

    @Test
    void memoryOnlyTargetDoesNotSearchDocumentChunks() {
        FakeContextSearchRepository contextSearchRepository = new FakeContextSearchRepository();
        contextSearchRepository.memoryVectorRows = List.of(memoryRow("memory-1"));
        ContextSearchService service = new ContextSearchService(
                contextSearchRepository,
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                10
        ));

        assertEquals(1, results.size());
        assertEquals(ContextSearchResultType.MEMORY, results.getFirst().type());
    }

    @Test
    void documentSearchLimitsChunksPerDocument() {
        FakeContextSearchRepository contextSearchRepository = new FakeContextSearchRepository();
        contextSearchRepository.documentVectorRows = List.of(
                chunkRow("doc-1", "chunk-1", 0),
                chunkRow("doc-1", "chunk-2", 1),
                chunkRow("doc-1", "chunk-3", 2),
                chunkRow("doc-2", "chunk-4", 0)
        );
        contextSearchRepository.documentTextRows = List.of(chunkRow("doc-1", "chunk-5", 3));
        ContextSearchService service = new ContextSearchService(
                contextSearchRepository,
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.DOCUMENT_ONLY,
                10
        ));

        assertEquals(List.of("chunk-1", "chunk-2", "chunk-4"), results.stream()
                .map(ContextSearchResult::documentChunkId)
                .toList());
    }

    @Test
    void scoreMergingBoostsResultsFoundInBothVectorAndText() {
        FakeContextSearchRepository contextSearchRepository = new FakeContextSearchRepository();
        // memory-1 appears in both vector and text results → should rank higher
        contextSearchRepository.memoryVectorRows = List.of(
                memoryRow("memory-2"),
                memoryRow("memory-1")
        );
        contextSearchRepository.memoryTextRows = List.of(
                memoryRow("memory-1")
        );
        ContextSearchService service = new ContextSearchService(
                contextSearchRepository,
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                10
        ));

        assertEquals(2, results.size());
        // memory-1 should be ranked first because it appears in both vector and text
        assertEquals("memory-1", results.get(0).memoryId());
        assertTrue(results.get(0).score() > results.get(1).score(),
                "Merged result should have a higher score than single-source result");
    }

    @Test
    void sessionScopedMemoryRanksAbovePublicScope() {
        FakeContextSearchRepository contextSearchRepository = new FakeContextSearchRepository();
        contextSearchRepository.memoryVectorRows = List.of(
                memoryRowWithScope("memory-public", Memory.SCOPE_PUBLIC),
                memoryRowWithScope("memory-session", Memory.SCOPE_SESSION)
        );
        ContextSearchService service = new ContextSearchService(
                contextSearchRepository,
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                10
        ));

        assertEquals(2, results.size());
        // Session-scoped memory should rank higher due to scopeBoost
        assertEquals("memory-session", results.get(0).memoryId());
    }

    @Test
    void recentMemoryRanksAboveOlderMemory() {
        FakeContextSearchRepository contextSearchRepository = new FakeContextSearchRepository();
        contextSearchRepository.memoryVectorRows = List.of(
                memoryRowWithAge("memory-old", 60),
                memoryRowWithAge("memory-new", 1)
        );
        ContextSearchService service = new ContextSearchService(
                contextSearchRepository,
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                10
        ));

        assertEquals(2, results.size());
        assertEquals("memory-new", results.get(0).memoryId());
    }

    @Test
    void nullQueryReturnsEmptyResults() {
        ContextSearchService service = new ContextSearchService(
                new FakeContextSearchRepository(),
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                null,
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                10
        ));

        assertTrue(results.isEmpty());
    }

    @Test
    void zeroLimitReturnsEmptyResults() {
        ContextSearchService service = new ContextSearchService(
                new FakeContextSearchRepository(),
                properties(),
                memoryEmbeddingService()
        );

        List<ContextSearchResult> results = service.search(new ContextSearchRequest(
                "deploy",
                "session-1",
                "user-1",
                false,
                ContextSearchTarget.MEMORY_ONLY,
                0
        ));

        assertTrue(results.isEmpty());
    }

    private ContextSearchProperties properties() {
        return new ContextSearchProperties();
    }

    private MemoryEmbeddingService memoryEmbeddingService() {
        return new MemoryEmbeddingService(new EmbeddingCapableProvider(), null, null);
    }

    private MemoryContextSearchRow memoryRow(String memoryId) {
        return new MemoryContextSearchRow(
                memoryId,
                "fact",
                Memory.SOURCE_USER,
                Memory.SCOPE_USER,
                "deploy on Fridays",
                OffsetDateTime.now()
        );
    }

    private MemoryContextSearchRow memoryRowWithScope(String memoryId, String scope) {
        return new MemoryContextSearchRow(
                memoryId,
                "fact",
                Memory.SOURCE_USER,
                scope,
                "deploy on Fridays",
                OffsetDateTime.now()
        );
    }

    private MemoryContextSearchRow memoryRowWithAge(String memoryId, int ageDays) {
        return new MemoryContextSearchRow(
                memoryId,
                "fact",
                Memory.SOURCE_USER,
                Memory.SCOPE_USER,
                "deploy on Fridays",
                OffsetDateTime.now().minusDays(ageDays)
        );
    }

    private DocumentChunkContextSearchRow chunkRow(String documentId, String chunkId, int chunkIndex) {
        return new DocumentChunkContextSearchRow(
                "memory-" + documentId,
                documentId,
                chunkId,
                "runbook.md",
                Memory.SCOPE_USER,
                "chunk " + chunkIndex,
                chunkIndex,
                OffsetDateTime.now()
        );
    }

    private static final class EmbeddingCapableProvider implements LLMProvider {

        @Override
        public LLMResponse<LLMGetResponse> getResponse(String input) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public <T> LLMResponse<T> getStructuredResponse(String input, Class<T> responseType) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public boolean supportsEmbeddings() {
            return true;
        }

        @Override
        public List<Float> getEmbeddings(String content) {
            return List.of(1.0f);
        }
    }

    private static final class FakeContextSearchRepository extends ContextSearchRepository {

        private List<MemoryContextSearchRow> memoryVectorRows = List.of();
        private List<MemoryContextSearchRow> memoryTextRows = List.of();
        private List<DocumentChunkContextSearchRow> documentVectorRows = List.of();
        private List<DocumentChunkContextSearchRow> documentTextRows = List.of();

        private FakeContextSearchRepository() {
            super(null);
        }

        @Override
        public List<MemoryContextSearchRow> findMemoryVectorCandidates(String embeddingVector, String sessionId, String ownerUserId, boolean isAdmin, int limit) {
            return memoryVectorRows;
        }

        @Override
        public List<MemoryContextSearchRow> findMemoryTextCandidates(String query, String sessionId, String ownerUserId, boolean isAdmin, int limit) {
            return memoryTextRows;
        }

        @Override
        public List<DocumentChunkContextSearchRow> findDocumentChunkVectorCandidates(String embeddingVector, String sessionId, String ownerUserId, boolean isAdmin, int limit) {
            return documentVectorRows;
        }

        @Override
        public List<DocumentChunkContextSearchRow> findDocumentChunkTextCandidates(String query, String sessionId, String ownerUserId, boolean isAdmin, int limit) {
            return documentTextRows;
        }
    }
}
