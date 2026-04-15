package com.lightflare.server.contextsearch;

import java.time.OffsetDateTime;

public record DocumentChunkContextSearchRow(
        String memoryId,
        String documentId,
        String documentChunkId,
        String title,
        String scope,
        String content,
        Integer chunkIndex,
        OffsetDateTime createdAt
) {
}
