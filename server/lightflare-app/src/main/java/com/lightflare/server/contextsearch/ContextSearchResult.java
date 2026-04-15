package com.lightflare.server.contextsearch;

import java.time.OffsetDateTime;

public record ContextSearchResult(
        ContextSearchResultType type,
        String memoryId,
        String documentId,
        String documentChunkId,
        String title,
        String source,
        String content,
        double score,
        OffsetDateTime createdAt
) {
}
