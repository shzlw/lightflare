package com.lightflare.server.chat;

import java.util.List;
import lombok.Builder;

@Builder
public record ChatMessagePageResponse(
    List<ChatMessageResponse> items,
    String nextBefore,
    boolean hasMore
) {
}
