package com.lightflare.server.user;

import java.util.List;
import lombok.Builder;

@Builder
public record UserPageResponse(
        List<UserResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
