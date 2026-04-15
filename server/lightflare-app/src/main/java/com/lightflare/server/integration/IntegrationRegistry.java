package com.lightflare.server.integration;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IntegrationRegistry {

    private final Map<String, Integration> integrationsById;

    public IntegrationRegistry(List<Integration> integrations) {
        this.integrationsById = new LinkedHashMap<>();
        for (Integration integration : integrations) {
            register(integration);
        }
    }

    private void register(Integration integration) {
        if (integration == null) {
            throw new IllegalStateException("Integration registry received a null integration bean");
        }

        IntegrationDefinition definition = integration.definition();
        if (definition == null) {
            throw new IllegalStateException("Integration " + integration.getClass().getName()
                    + " returned a null definition");
        }
        if (!StringUtils.hasText(definition.getId())) {
            throw new IllegalStateException("Integration " + integration.getClass().getName()
                    + " has an empty integration id");
        }
        if (!StringUtils.hasText(definition.getDisplayName())) {
            throw new IllegalStateException("Integration " + integration.getClass().getName()
                    + " has an empty integration displayName");
        }

        Integration existing = integrationsById.putIfAbsent(definition.getId(), integration);
        if (existing != null) {
            throw new IllegalStateException("Duplicate integration id '" + definition.getId()
                    + "' found for " + existing.getClass().getName()
                    + " and " + integration.getClass().getName());
        }
    }

    public Collection<Integration> list() {
        return List.copyOf(integrationsById.values());
    }

    public Optional<Integration> find(String integrationId) {
        return Optional.ofNullable(integrationsById.get(integrationId));
    }
}
