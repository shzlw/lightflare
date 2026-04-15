package com.lightflare.server.memory;

import java.util.List;
import lombok.Builder;

@Builder
public record MemoryPageResponse(
        List<MemoryResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
