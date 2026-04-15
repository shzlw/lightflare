package com.lightflare.server.skill;

import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record SkillResponse(
    String id,
    String name,
    String description,
    String visibility,
    String userId,
    String source,
    String content,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
