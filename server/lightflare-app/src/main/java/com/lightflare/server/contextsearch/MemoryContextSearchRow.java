package com.lightflare.server.contextsearch;

import java.time.OffsetDateTime;

public record MemoryContextSearchRow(
        String memoryId,
        String title,
        String source,
        String scope,
        String content,
        OffsetDateTime createdAt
) {
}
