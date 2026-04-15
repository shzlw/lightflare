package com.lightflare.server.integration;

import com.lightflare.server.integration.IntegrationResponse;
import com.lightflare.server.integration.Integration;
import com.lightflare.server.integration.IntegrationDefinition;
import com.lightflare.server.integration.IntegrationRegistry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntegrationCatalogService {

    private final IntegrationRegistry integrationRegistry;

    public List<IntegrationResponse> listIntegrations() {
        return integrationRegistry.list().stream()
                .map(Integration::definition)
                .sorted(Comparator.comparing(IntegrationDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(IntegrationDefinition::getId))
                .map(definition -> new IntegrationResponse(
                        definition.getId(),
                        definition.getDisplayName(),
                        definition.getDescription(),
                        definition.isEnabled()
                ))
                .toList();
    }
}
