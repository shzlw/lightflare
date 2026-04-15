package com.lightflare.server.memory;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record DocumentResponse(
        String id,
        String memoryId,
        String fileName,
        String filePath,
        Long fileSize,
        String fileContentType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
