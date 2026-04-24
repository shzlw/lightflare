package com.lightflare.server.application;

import java.util.List;
import lombok.Builder;

@Builder
public record ApplicationPageResponse(
        List<ApplicationResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
