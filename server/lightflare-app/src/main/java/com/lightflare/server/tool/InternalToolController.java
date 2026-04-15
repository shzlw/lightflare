package com.lightflare.server.tool;

import com.lightflare.server.tool.ToolCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/tools")
public class InternalToolController {

    private final ToolCatalogService toolCatalogService;

    @GetMapping
    public List<ToolResponse> listTools() {
        return toolCatalogService.listTools();
    }
}
