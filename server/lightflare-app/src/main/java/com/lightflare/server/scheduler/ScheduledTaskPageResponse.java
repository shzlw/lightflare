package com.lightflare.server.scheduler;

import java.util.List;
import lombok.Builder;

@Builder
public record ScheduledTaskPageResponse(
        List<ScheduledTaskResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
