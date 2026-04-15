package com.lightflare.server.skill;

import java.util.List;
import lombok.Builder;

@Builder
public record SkillPageResponse(
    List<SkillResponse> items,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
}
