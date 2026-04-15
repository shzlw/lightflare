package com.lightflare.server.chat;

import java.util.List;
import lombok.Builder;

@Builder
public record ChatSessionPageResponse(
    List<ChatSessionResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
}
