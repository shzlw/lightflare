package com.lightflare.server.integration;

import com.lightflare.server.integration.IntegrationCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/integrations")
public class InternalIntegrationController {

    private final IntegrationCatalogService integrationCatalogService;

    @GetMapping
    public List<IntegrationResponse> listIntegrations() {
        return integrationCatalogService.listIntegrations();
    }
}
