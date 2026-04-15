package com.lightflare.server.integration;

import com.lightflare.server.integration.McpCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/mcps")
public class InternalMcpController {

    private final McpCatalogService mcpCatalogService;

    @GetMapping
    public List<McpResponse> listMcps() {
        return mcpCatalogService.listMcps();
    }
}
