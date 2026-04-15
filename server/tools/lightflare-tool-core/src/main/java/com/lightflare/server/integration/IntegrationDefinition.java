package com.lightflare.server.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationDefinition {

    private String id;

    private String displayName;

    private String description;

    private boolean enabled;
}
