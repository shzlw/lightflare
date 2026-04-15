package com.lightflare.server.tools.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInputDefinition {

    private String name;

    private String type;

    private boolean required;

    @Builder.Default
    private List<ToolInputDefinition> properties = List.of();
}
