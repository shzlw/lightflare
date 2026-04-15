package com.lightflare.server.memory;

import com.lightflare.server.agent.memory.MemoryEmbeddingService;
import com.lightflare.server.auth.AppRoles;
import com.lightflare.server.auth.AuthService;
import com.lightflare.server.auth.UserContext;
import com.lightflare.server.contextsearch.ContextSearchProperties;
import com.lightflare.server.contextsearch.ContextSearchRequest;
import com.lightflare.server.contextsearch.ContextSearchService;
import com.lightflare.server.contextsearch.ContextSearchTarget;
import com.lightflare.server.utils.DateUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final MemoryFileStorageService memoryFileStorageService;
    private final DocumentChunkingService documentChunkingService;
    private final ContextSearchService contextSearchService;
    private final ContextSearchProperties contextSearchProperties;
    private final AuthService authService;

    public MemoryPageResponse listMemories(int page,
                                           int size,
                                           String query,
                                           String sessionId,
                                           String ownerUserId,
                                           String scope,
                                           String kind,
                                           String status,
                                           String createdAtSort,
                                           HttpServletRequest httpRequest) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        String normalizedQuery = normalize(query);
        String normalizedSessionId = normalize(sessionId);
        UserContext userContext = authService.requireUserContext(httpRequest);
        String normalizedOwnerUserId = normalizeOwnerUserIdFilter(ownerUserId, userContext);
        String normalizedScope = normalize(scope);
        String normalizedKind = normalize(kind);
        String normalizedStatus = normalize(status);
        String normalizedCreatedAtSort = normalizeCreatedAtSort(createdAtSort);

        if (StringUtils.hasText(normalizedQuery)) {
            return searchMemoriesByContext(
                    normalizedPage,
                    normalizedSize,
                    normalizedQuery,
                    normalizedSessionId,
                    normalizedOwnerUserId,
                    normalizedScope,
                    normalizedKind,
                    normalizedStatus,
                    userContext
            );
        }

        long totalItems = memoryRepository.countMemories(
                normalizedQuery,
                userContext.userId(),
                isAdmin(userContext),
                normalizedSessionId,
                normalizedOwnerUserId,
                normalizedScope,
                normalizedKind,
                normalizedStatus
        );
        List<MemoryResponse> items = memoryRepository.findMemoriesPage(
                        normalizedQuery,
                        userContext.userId(),
                        isAdmin(userContext),
                        normalizedSessionId,
                        normalizedOwnerUserId,
                        normalizedScope,
                        normalizedKind,
                        normalizedStatus,
                        normalizedCreatedAtSort,
                        normalizedSize,
                        (long) normalizedPage * normalizedSize
                ).stream()
                .map(this::toResponse)
                .toList();

        return MemoryPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .totalItems(totalItems)
                .totalPages((int) Math.ceil(totalItems / (double) normalizedSize))
                .build();
    }

    private MemoryPageResponse searchMemoriesByContext(int page,
                                                       int size,
                                                       String query,
                                                       String sessionId,
                                                       String ownerUserId,
                                                       String scope,
                                                       String kind,
                                                       String status,
                                                       UserContext userContext) {
        List<String> rankedMemoryIds = contextSearchService.searchMemoryIds(new ContextSearchRequest(
                query,
                sessionId,
                isAdmin(userContext) ? ownerUserId : userContext.userId(),
                isAdmin(userContext),
                ContextSearchTarget.MEMORY_AND_DOCUMENT,
                contextSearchProperties.getMemoryPageSearchLimit()
        ));
        if (rankedMemoryIds.isEmpty()) {
            return MemoryPageResponse.builder()
                    .items(List.of())
                    .page(page)
                    .size(size)
                    .totalItems(0)
                    .totalPages(0)
                    .build();
        }

        Map<String, Integer> rankByMemoryId = new HashMap<>();
        for (int index = 0; index < rankedMemoryIds.size(); index++) {
            rankByMemoryId.put(rankedMemoryIds.get(index), index);
        }

        List<Memory> filteredMemories = new ArrayList<>(memoryRepository.findByMemoryIds(rankedMemoryIds).stream()
                .filter(memory -> isSearchResultAccessible(memory, userContext))
                .filter(memory -> !StringUtils.hasText(sessionId) || sessionId.equals(memory.getSessionId()))
                .filter(memory -> !StringUtils.hasText(ownerUserId) || ownerUserId.equals(memory.getOwnerUserId()))
                .filter(memory -> !StringUtils.hasText(scope) || scope.equals(memory.getScope()))
                .filter(memory -> !StringUtils.hasText(kind) || kind.equals(memory.getKind()))
                .filter(memory -> !StringUtils.hasText(status) || status.equals(memory.getStatus()))
                .toList());
        filteredMemories.sort(Comparator.comparingInt(memory -> rankByMemoryId.getOrDefault(memory.getId(), Integer.MAX_VALUE)));

        int fromIndex = Math.min(page * size, filteredMemories.size());
        int toIndex = Math.min(fromIndex + size, filteredMemories.size());
        List<MemoryResponse> items = filteredMemories.subList(fromIndex, toIndex).stream()
                .map(this::toResponse)
                .toList();

        return MemoryPageResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalItems(filteredMemories.size())
                .totalPages((int) Math.ceil(filteredMemories.size() / (double) size))
                .build();
    }

    public MemoryResponse getMemory(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        return toResponse(findAccessibleMemory(id, userContext));
    }

    @Transactional
    public MemoryResponse createMemory(CreateMemoryRequest request, HttpServletRequest httpRequest) {
        return createMemory(
                request.getOwnerUserId(),
                request.getSessionId(),
                request.getScope(),
                request.getKind(),
                request.getSource(),
                request.getRetentionPolicy(),
                request.getContent(),
                null,
                httpRequest
        );
    }

    @Transactional
    public MemoryResponse createMemory(String ownerUserId,
                                       String sessionId,
                                       String scope,
                                       String kind,
                                       String source,
                                       String retentionPolicy,
                                       String content,
                                       MultipartFile file,
                                       HttpServletRequest httpRequest) {
        String effectiveContent = normalize(content);
        MemoryFileStorageService.StoredMemoryFile storedFile = null;
        if (file != null && !file.isEmpty()) {
            storedFile = memoryFileStorageService.storeFile(file);
        }

        if (!StringUtils.hasText(effectiveContent) && storedFile == null) {
            throw new IllegalArgumentException("Memory content or file is required");
        }

        Memory memory = buildMemory(
                ownerUserId,
                sessionId,
                scope,
                storedFile == null ? kind : Memory.KIND_DOCUMENT,
                source,
                retentionPolicy,
                StringUtils.hasText(effectiveContent)
                        ? effectiveContent
                        : "Uploaded document: " + storedFile.originalFileName(),
                httpRequest
        );

        insertMemory(memory);
        if (StringUtils.hasText(effectiveContent)) {
            memoryEmbeddingService.generateEmbedding(memory.getId(), memory.getContent());
        }
        if (storedFile != null) {
            Document document = createDocument(memory, storedFile);
            createDocumentChunks(document, storedFile.extractedContent());
        }
        return toResponse(memory);
    }

    public MemoryResponse archiveMemory(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        Memory memory = findAccessibleOwnedMemory(id, userContext);
        if (!Memory.STATUS_ACTIVE.equals(memory.getStatus())) {
            throw new IllegalStateException("Only active memories can be archived");
        }

        int updated = memoryRepository.updateStatusById(
                id,
                Memory.STATUS_ARCHIVED,
                Memory.STATUS_REASON_MANUAL,
                DateUtils.now(),
                null,
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one memory row to be archived but got " + updated);
        }
        return toResponse(findExistingMemory(id));
    }

    public void deleteMemory(String id, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        findAccessibleOwnedMemory(id, userContext);
        int updated = memoryRepository.updateStatusById(
                id,
                Memory.STATUS_DELETED,
                Memory.STATUS_REASON_USER_DELETED,
                DateUtils.now(),
                null,
                DateUtils.now()
        );
        if (updated != 1) {
            throw new IllegalStateException("Expected one memory row to be deleted but got " + updated);
        }
    }

    private Memory findExistingMemory(String id) {
        return memoryRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + id));
    }

    private Memory findAccessibleMemory(String id, UserContext userContext) {
        Memory memory = findExistingMemory(id);
        if (!isSearchResultAccessible(memory, userContext)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found");
        }
        return memory;
    }

    private boolean isSearchResultAccessible(Memory memory, UserContext userContext) {
        return isAdmin(userContext)
                || Memory.SCOPE_PUBLIC.equals(memory.getScope())
                || StringUtils.pathEquals(userContext.userId(), memory.getOwnerUserId());
    }

    private Memory findAccessibleOwnedMemory(String id, UserContext userContext) {
        Memory memory = findExistingMemory(id);
        if (!isAdmin(userContext) && !StringUtils.pathEquals(userContext.userId(), memory.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found");
        }
        return memory;
    }

    private Memory buildMemory(String ownerUserId,
                               String sessionId,
                               String scope,
                               String kind,
                               String source,
                               String retentionPolicy,
                               String content,
                               HttpServletRequest httpRequest) {
        String effectiveScope = StringUtils.hasText(normalize(scope)) ? normalize(scope) : Memory.SCOPE_USER;
        String effectiveKind = StringUtils.hasText(normalize(kind)) ? normalize(kind) : Memory.KIND_KNOWLEDGE_NOTE;
        String effectiveSource = StringUtils.hasText(normalize(source)) ? normalize(source) : Memory.SOURCE_USER;
        String effectiveRetentionPolicy = StringUtils.hasText(normalize(retentionPolicy))
                ? normalize(retentionPolicy)
                : Memory.RETENTION_POLICY_PRESERVE_RAW;
        String effectiveSessionId = normalize(sessionId);
        String effectiveOwnerUserId = resolveOwnerUserId(ownerUserId, effectiveScope, httpRequest);

        validateScope(effectiveScope, effectiveSessionId, effectiveOwnerUserId);
        validateEnumeratedField(
                effectiveKind,
                List.of(
                        Memory.KIND_CHAT_MESSAGE,
                        Memory.KIND_KNOWLEDGE_NOTE,
                        Memory.KIND_SUMMARY,
                        Memory.KIND_FACT,
                        Memory.KIND_TOOL_RESULT,
                        Memory.KIND_DOCUMENT
                ),
                "Memory kind must be one of: chat_message, knowledge_note, summary, fact, tool_result, document"
        );
        validateEnumeratedField(
                effectiveSource,
                List.of(Memory.SOURCE_USER, Memory.SOURCE_AGENT, Memory.SOURCE_SYSTEM, Memory.SOURCE_IMPORT),
                "Memory source must be one of: user, agent, system, import"
        );
        validateEnumeratedField(
                effectiveRetentionPolicy,
                List.of(Memory.RETENTION_POLICY_COMPACTABLE, Memory.RETENTION_POLICY_PRESERVE_RAW),
                "Memory retentionPolicy must be one of: compactable, preserve_raw"
        );

        Memory memory = new Memory();
        memory.setId(UUID.randomUUID().toString());
        memory.setOwnerUserId(effectiveOwnerUserId);
        memory.setSessionId(effectiveSessionId);
        memory.setScope(effectiveScope);
        memory.setKind(effectiveKind);
        memory.setSource(effectiveSource);
        memory.setRetentionPolicy(effectiveRetentionPolicy);
        memory.setStatus(Memory.STATUS_ACTIVE);
        memory.setStatusReason(null);
        memory.setStatusChangedAt(null);
        memory.setStatusChangedBy(null);
        memory.setContent(content);
        memory.setCreatedAt(DateUtils.now());
        memory.setUpdatedAt(memory.getCreatedAt());
        return memory;
    }

    private String resolveOwnerUserId(String ownerUserId, String scope, HttpServletRequest httpRequest) {
        UserContext userContext = authService.requireUserContext(httpRequest);
        if (Memory.SCOPE_PUBLIC.equals(scope)) {
            if (!isAdmin(userContext)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins may create public memories");
            }
            return null;
        }
        if (!isAdmin(userContext)) {
            if (StringUtils.hasText(ownerUserId) && !ownerUserId.trim().equals(userContext.userId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot create memories for another user");
            }
            return userContext.userId();
        }
        if (StringUtils.hasText(ownerUserId)) {
            return ownerUserId.trim();
        }
        return userContext.userId();
    }

    private String normalizeOwnerUserIdFilter(String ownerUserId, UserContext userContext) {
        String normalizedOwnerUserId = normalize(ownerUserId);
        if (isAdmin(userContext)) {
            return normalizedOwnerUserId;
        }
        if (StringUtils.hasText(normalizedOwnerUserId) && !normalizedOwnerUserId.equals(userContext.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot query memories for another user");
        }
        return normalizedOwnerUserId;
    }

    private boolean isAdmin(UserContext userContext) {
        return userContext != null && AppRoles.isAdminLike(userContext.role());
    }

    private void validateScope(String scope, String sessionId, String ownerUserId) {
        validateEnumeratedField(
                scope,
                List.of(Memory.SCOPE_SESSION, Memory.SCOPE_USER, Memory.SCOPE_PUBLIC),
                "Memory scope must be one of: session, user, public"
        );

        if (Memory.SCOPE_SESSION.equals(scope)) {
            if (!StringUtils.hasText(sessionId)) {
                throw new IllegalArgumentException("Session scope memories require a sessionId");
            }
            if (!StringUtils.hasText(ownerUserId)) {
                throw new IllegalArgumentException("Session scope memories require an ownerUserId");
            }
        }
        if (Memory.SCOPE_USER.equals(scope) && !StringUtils.hasText(ownerUserId)) {
            throw new IllegalArgumentException("User scope memories require an ownerUserId");
        }
        if (!Memory.SCOPE_SESSION.equals(scope) && StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("Only session scope memories may include a sessionId");
        }
    }

    private void validateEnumeratedField(String value, List<String> allowedValues, String message) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void insertMemory(Memory memory) {
        int inserted = memoryRepository.insert(
                memory.getId(),
                memory.getOwnerUserId(),
                memory.getSessionId(),
                memory.getScope(),
                memory.getKind(),
                memory.getRetentionPolicy(),
                memory.getSource(),
                memory.getStatus(),
                memory.getStatusReason(),
                memory.getStatusChangedAt(),
                memory.getStatusChangedBy(),
                memory.getContent(),
                null
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one memory row to be inserted but got " + inserted);
        }
    }

    private Document createDocument(Memory memory, MemoryFileStorageService.StoredMemoryFile storedFile) {
        Document document = new Document();
        document.setId(UUID.randomUUID().toString());
        document.setMemoryId(memory.getId());
        document.setFileName(storedFile.originalFileName());
        document.setFilePath(storedFile.storedPath());
        document.setFileSize(storedFile.size());
        document.setFileContentType(storedFile.contentType());
        document.setCreatedAt(DateUtils.now());
        document.setUpdatedAt(document.getCreatedAt());

        int inserted = documentRepository.insert(
                document.getId(),
                document.getMemoryId(),
                document.getFileName(),
                document.getFilePath(),
                document.getFileSize(),
                document.getFileContentType(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one document row to be inserted but got " + inserted);
        }
        return document;
    }

    private void createDocumentChunks(Document document, String extractedContent) {
        List<String> chunks = documentChunkingService.splitIntoChunks(extractedContent);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file does not contain readable text chunks");
        }

        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index);
            chunk.setContent(chunks.get(index));
            chunk.setCreatedAt(DateUtils.now());
            chunk.setUpdatedAt(chunk.getCreatedAt());

            int inserted = documentChunkRepository.insert(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    null,
                    chunk.getCreatedAt(),
                    chunk.getUpdatedAt()
            );
            if (inserted != 1) {
                throw new IllegalStateException("Expected one document_chunk row to be inserted but got " + inserted);
            }
            memoryEmbeddingService.generateDocumentChunkEmbedding(chunk.getId(), chunk.getContent());
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCreatedAtSort(String value) {
        String normalized = normalize(value);
        if ("asc".equalsIgnoreCase(normalized)) {
            return "asc";
        }
        return "desc";
    }

    private MemoryResponse toResponse(Memory memory) {
        return MemoryResponse.builder()
                .id(memory.getId())
                .ownerUserId(memory.getOwnerUserId())
                .sessionId(memory.getSessionId())
                .scope(memory.getScope())
                .kind(memory.getKind())
                .source(memory.getSource())
                .retentionPolicy(memory.getRetentionPolicy())
                .status(memory.getStatus())
                .statusReason(memory.getStatusReason())
                .statusChangedAt(memory.getStatusChangedAt())
                .statusChangedBy(memory.getStatusChangedBy())
                .document(documentRepository.findByMemoryId(memory.getId())
                        .map(this::toDocumentResponse)
                        .orElse(null))
                .content(memory.getContent())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .build();
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .memoryId(document.getMemoryId())
                .fileName(document.getFileName())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .fileContentType(document.getFileContentType())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
