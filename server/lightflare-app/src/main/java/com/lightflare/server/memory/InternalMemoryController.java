package com.lightflare.server.memory;

import com.lightflare.server.memory.MemoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal-api/v1/memories")
public class InternalMemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public MemoryPageResponse listMemories(@RequestParam(name = "page", defaultValue = "0") int page,
                                           @RequestParam(name = "size", defaultValue = "20") int size,
                                           @RequestParam(name = "q", required = false) String q,
                                           @RequestParam(name = "sessionId", required = false) String sessionId,
                                           @RequestParam(name = "ownerUserId", required = false) String ownerUserId,
                                           @RequestParam(name = "scope", required = false) String scope,
                                           @RequestParam(name = "kind", required = false) String kind,
                                           @RequestParam(name = "status", required = false) String status,
                                           @RequestParam(name = "createdAtSort", defaultValue = "desc") String createdAtSort,
                                           HttpServletRequest httpRequest) {
        return memoryService.listMemories(page, size, q, sessionId, ownerUserId, scope, kind, status, createdAtSort, httpRequest);
    }

    @GetMapping("/{id}")
    public MemoryResponse getMemory(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return memoryService.getMemory(id, httpRequest);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse createMemory(@RequestBody CreateMemoryRequest request, HttpServletRequest httpRequest) {
        return memoryService.createMemory(request, httpRequest);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse createMemoryMultipart(@RequestParam(name = "ownerUserId", required = false) String ownerUserId,
                                                @RequestParam(name = "sessionId", required = false) String sessionId,
                                                @RequestParam(name = "scope", required = false) String scope,
                                                @RequestParam(name = "kind", required = false) String kind,
                                                @RequestParam(name = "source", required = false) String source,
                                                @RequestParam(name = "retentionPolicy", required = false) String retentionPolicy,
                                                @RequestParam(name = "content", required = false) String content,
                                                @RequestParam(name = "file", required = false) MultipartFile file,
                                                HttpServletRequest httpRequest) {
        return memoryService.createMemory(
                ownerUserId,
                sessionId,
                scope,
                kind,
                source,
                retentionPolicy,
                content,
                file,
                httpRequest
        );
    }

    @PostMapping("/{id}/archive")
    public MemoryResponse archiveMemory(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        return memoryService.archiveMemory(id, httpRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(@PathVariable("id") String id, HttpServletRequest httpRequest) {
        memoryService.deleteMemory(id, httpRequest);
    }
}
