package com.lightflare.server.project;

import java.util.List;
import lombok.Builder;

@Builder
public record ProjectPageResponse(
        List<ProjectResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
