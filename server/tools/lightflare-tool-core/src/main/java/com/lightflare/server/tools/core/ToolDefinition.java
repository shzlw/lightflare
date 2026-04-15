package com.lightflare.server.tools.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    private String name;

    private String description;

    private String category;

    private String integrationId;

    private String usageGuidance;

    @Builder.Default
    private java.util.List<ToolInputDefinition> properties = java.util.List.of();
}
